package com.techstore.sifen;

import com.techstore.config.EmpresaProperties;
import com.techstore.entity.Factura;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.service.EmpresaConfigService;
import com.techstore.util.Dinero;
import com.techstore.util.RucParaguayo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Genera el XML del Documento Electrónico (DE) de una factura según el
 * Manual Técnico del SIFEN v150 (implementación demostrativa del estándar:
 * grupos principales de una Factura Electrónica de venta al contado).
 */
@Service
@RequiredArgsConstructor
public class DocumentoElectronicoService {

    private static final String NS = "http://ekuatia.set.gov.py/sifen/xsd";
    private static final String VERSION = "150";
    private static final String TIPO_DOC_FE = "01"; // Factura electrónica
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EmpresaConfigService empresaConfig;

    /** Datos vigentes del emisor (editables desde la app). */
    private EmpresaProperties empresa() {
        return empresaConfig.actual();
    }

    /** Resultado: el CDC calculado y el XML del documento electrónico. */
    public record DocumentoElectronico(String cdc, String xml) {
    }

    public DocumentoElectronico generar(Factura factura) {
        try {
            return construir(factura);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("No se pudo generar el documento electrónico: " + ex.getMessage());
        }
    }

    private DocumentoElectronico construir(Factura factura) throws Exception {
        String establecimiento = empresa().establecimiento();
        String puntoExpedicion = empresa().puntoExpedicion();
        // El número del CDC/timbrado usa 7 dígitos; tomamos el correlativo de la factura.
        String numeroDoc = String.format("%07d", numeroCorrelativo(factura));
        // Se fija al emitir la factura: si se regenerara acá, el CDC (y con él el QR y
        // la consulta al SIFEN) cambiaría en cada descarga del mismo documento.
        String codigoSeguridad = factura.getCodigoSeguridad() == null
                ? CodigoControl.generarCodigoSeguridad()
                : factura.getCodigoSeguridad();

        String cdc = CodigoControl.generar(
                TIPO_DOC_FE, empresa().rucNumero(), empresa().rucDv(), establecimiento, puntoExpedicion,
                numeroDoc, empresa().tipoContribuyente(), factura.getFecha().toLocalDate(), 1, codigoSeguridad);

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element rDE = doc.createElementNS(NS, "rDE");
        doc.appendChild(rDE);
        hijo(doc, rDE, "dVerFor", VERSION);

        Element de = hijo(doc, rDE, "DE", null);
        de.setAttribute("Id", cdc);
        hijo(doc, de, "dDVId", String.valueOf(cdc.charAt(43)));
        hijo(doc, de, "dFecFirma", factura.getFecha().format(DT));
        hijo(doc, de, "dSisFact", "1");

        // gOpeDE — operación del DE
        Element gOpeDE = hijo(doc, de, "gOpeDE", null);
        hijo(doc, gOpeDE, "iTipEmi", "1");
        hijo(doc, gOpeDE, "dDesTipEmi", "Normal");
        hijo(doc, gOpeDE, "dCodSeg", codigoSeguridad);

        // gTimb — timbrado
        Element gTimb = hijo(doc, de, "gTimb", null);
        hijo(doc, gTimb, "iTiDE", "1");
        hijo(doc, gTimb, "dDesTiDE", "Factura electrónica");
        hijo(doc, gTimb, "dNumTim", empresa().timbrado());
        hijo(doc, gTimb, "dEst", establecimiento);
        hijo(doc, gTimb, "dPunExp", puntoExpedicion);
        hijo(doc, gTimb, "dNumDoc", numeroDoc);
        hijo(doc, gTimb, "dFeIniT", empresa().timbradoInicioVigencia());

        // gDatGralOpe — datos generales
        Element gDat = hijo(doc, de, "gDatGralOpe", null);
        hijo(doc, gDat, "dFeEmiDE", factura.getFecha().format(DT));

        Element gOpeCom = hijo(doc, gDat, "gOpeCom", null);
        hijo(doc, gOpeCom, "iTipTra", "1");
        hijo(doc, gOpeCom, "dDesTipTra", "Venta de mercadería");
        hijo(doc, gOpeCom, "iTImp", "1");
        hijo(doc, gOpeCom, "dDesTImp", "IVA");
        hijo(doc, gOpeCom, "cMoneOpe", factura.getMoneda().codigo());
        hijo(doc, gOpeCom, "dDesMoneOpe", factura.getMoneda().nombre());

        agregarEmisor(doc, gDat);
        agregarReceptor(doc, gDat, factura);

        // gDtipDE — específico por tipo (factura) + condición + items
        Element gDtip = hijo(doc, de, "gDtipDE", null);
        Element gCamFE = hijo(doc, gDtip, "gCamFE", null);
        hijo(doc, gCamFE, "iIndPres", "1");
        hijo(doc, gCamFE, "dDesIndPres", "Operación presencial");

        Element gCamCond = hijo(doc, gDtip, "gCamCond", null);
        hijo(doc, gCamCond, "iCondOpe", "1");
        hijo(doc, gCamCond, "dDCondOpe", "Contado");
        Element gPaConEIni = hijo(doc, gCamCond, "gPaConEIni", null);
        hijo(doc, gPaConEIni, "iTiPago", "1");
        hijo(doc, gPaConEIni, "dDesTiPag", "Efectivo");
        hijo(doc, gPaConEIni, "dMonTiPag", plano(factura.getTotal()));
        hijo(doc, gPaConEIni, "cMoneTiPag", factura.getMoneda().codigo());
        hijo(doc, gPaConEIni, "dDMoneTiPag", factura.getMoneda().nombre());

        for (PedidoDetalle detalle : factura.getPedido().getDetalles()) {
            agregarItem(doc, gDtip, detalle);
        }

        agregarTotales(doc, de, factura);

        return new DocumentoElectronico(cdc, serializar(doc));
    }

    private void agregarEmisor(Document doc, Element gDat) {
        Element g = hijo(doc, gDat, "gEmis", null);
        hijo(doc, g, "dRucEm", empresa().rucNumero());
        hijo(doc, g, "dDVEmi", String.valueOf(empresa().rucDv()));
        hijo(doc, g, "iTipCont", String.valueOf(empresa().tipoContribuyente()));
        hijo(doc, g, "dNomEmi", empresa().nombre());
        hijo(doc, g, "dDirEmi", empresa().direccion());
        hijo(doc, g, "dNumCas", "0");
        hijo(doc, g, "cDepEmi", empresa().departamentoCodigo());
        hijo(doc, g, "dDesDepEmi", empresa().departamentoNombre());
        hijo(doc, g, "cDisEmi", empresa().distritoCodigo());
        hijo(doc, g, "dDesDisEmi", empresa().distritoNombre());
        hijo(doc, g, "cCiuEmi", empresa().ciudadCodigo());
        hijo(doc, g, "dDesCiuEmi", empresa().ciudadNombre());
        hijo(doc, g, "dTelEmi", empresa().telefono());
        Element gAct = hijo(doc, g, "gActEco", null);
        hijo(doc, gAct, "cActEco", empresa().actividadCodigo());
        hijo(doc, gAct, "dDesActEco", empresa().actividad());
    }

    private void agregarReceptor(Document doc, Element gDat, Factura factura) {
        Usuario cliente = factura.getPedido().getUsuario();
        String ruc = factura.getReceptorRuc();
        Element g = hijo(doc, gDat, "gDatRec", null);

        if (ruc == null) {
            // Sin RUC: receptor no contribuyente (B2C), innominado.
            hijo(doc, g, "iNatRec", "2");
            hijo(doc, g, "iTiOpe", "2");
            hijo(doc, g, "cPaisRec", "PRY");
            hijo(doc, g, "dDesPaisRe", "Paraguay");
            hijo(doc, g, "iTipIDRec", "5");
            hijo(doc, g, "dDTipIDRec", "Innominado");
            hijo(doc, g, "dNumIDRec", "0");
            hijo(doc, g, "dNomRec", cliente.getNombre() + " " + cliente.getApellido());
        } else {
            // Con RUC: contribuyente (B2B). El RUC va sin el dígito verificador,
            // que se informa aparte en dDVRec.
            hijo(doc, g, "iNatRec", "1");
            hijo(doc, g, "iTiOpe", "1");
            hijo(doc, g, "cPaisRec", "PRY");
            hijo(doc, g, "dDesPaisRe", "Paraguay");
            hijo(doc, g, "dRucRec", RucParaguayo.numero(ruc));
            hijo(doc, g, "dDVRec", String.valueOf(RucParaguayo.dv(ruc)));
            hijo(doc, g, "dNomRec", factura.getReceptorNombre());
        }
        // El del receptor si se informó; si no, el del dueño del pedido.
        hijo(doc, g, "dEmailRec", factura.getReceptorEmail() != null
                ? factura.getReceptorEmail()
                : cliente.getEmail());
    }

    private void agregarItem(Document doc, Element gDtip, PedidoDetalle detalle) {
        BigDecimal tasa = detalle.getIvaAplicado() == null ? BigDecimal.ZERO : detalle.getIvaAplicado();
        Dinero subtotal = detalle.getSubtotal();
        Dinero iva = subtotal.ivaIncluido(tasa);
        Dinero baseGravada = subtotal.menos(iva);

        Element g = hijo(doc, gDtip, "gCamItem", null);
        hijo(doc, g, "dCodInt", String.valueOf(detalle.getProducto().getId()));
        hijo(doc, g, "dDesProSer", detalle.getProducto().getNombre());
        hijo(doc, g, "cUniMed", "77");
        hijo(doc, g, "dDesUniMed", "UNI");
        hijo(doc, g, "dCantProSer", String.valueOf(detalle.getCantidad()));

        Element gVal = hijo(doc, g, "gValorItem", null);
        hijo(doc, gVal, "dPUniProSer", plano(detalle.getPrecioUnitario()));
        hijo(doc, gVal, "dTotBruOpeItem", plano(subtotal));
        Element gResta = hijo(doc, gVal, "gValorRestaItem", null);
        hijo(doc, gResta, "dTotOpeItem", plano(subtotal));

        Element gIVA = hijo(doc, g, "gCamIVA", null);
        hijo(doc, gIVA, "iAfectIVA", tasa.signum() > 0 ? "1" : "3");
        hijo(doc, gIVA, "dDesAfecIVA", tasa.signum() > 0 ? "Gravado IVA" : "Exento");
        hijo(doc, gIVA, "dPropIVA", "100");
        hijo(doc, gIVA, "dTasaIVA", tasa.stripTrailingZeros().toPlainString());
        hijo(doc, gIVA, "dBasGravIVA", plano(baseGravada));
        hijo(doc, gIVA, "dLiqIVAItem", plano(iva));
    }

    private void agregarTotales(Document doc, Element de, Factura factura) {
        Dinero grav10 = Dinero.cero(factura.getMoneda());
        Dinero grav5 = Dinero.cero(factura.getMoneda());
        Dinero exentas = Dinero.cero(factura.getMoneda());
        for (PedidoDetalle d : factura.getPedido().getDetalles()) {
            int tasa = d.getIvaAplicado() == null ? 0 : d.getIvaAplicado().intValue();
            Dinero monto = d.getSubtotal();
            switch (tasa) {
                case 10 -> grav10 = grav10.mas(monto);
                case 5 -> grav5 = grav5.mas(monto);
                default -> exentas = exentas.mas(monto);
            }
        }
        // El mismo cálculo que guarda la factura y que imprime el KuDE.
        Dinero iva10 = grav10.ivaIncluido(BigDecimal.TEN);
        Dinero iva5 = grav5.ivaIncluido(BigDecimal.valueOf(5));

        Element g = hijo(doc, de, "gTotSub", null);
        hijo(doc, g, "dSubExe", plano(exentas));
        hijo(doc, g, "dSub5", plano(grav5));
        hijo(doc, g, "dSub10", plano(grav10));
        hijo(doc, g, "dTotOpe", plano(factura.getTotal()));
        hijo(doc, g, "dTotGralOpe", plano(factura.getTotal()));
        hijo(doc, g, "dIVA5", plano(iva5));
        hijo(doc, g, "dIVA10", plano(iva10));
        hijo(doc, g, "dTotIVA", plano(iva5.mas(iva10)));
    }

    private static int numeroCorrelativo(Factura factura) {
        // El número de factura tiene formato 001-001-XXXXXXX; tomamos el correlativo.
        String numero = factura.getNumero();
        String ultimo = numero.contains("-") ? numero.substring(numero.lastIndexOf('-') + 1) : numero;
        return Integer.parseInt(ultimo.replaceAll("\\D", ""));
    }

    private static Element hijo(Document doc, Element padre, String nombre, String texto) {
        Element e = doc.createElementNS(NS, nombre);
        if (texto != null) {
            e.setTextContent(texto);
        }
        padre.appendChild(e);
        return e;
    }

    private static String plano(Dinero valor) {
        return valor.monto().stripTrailingZeros().toPlainString();
    }

    private static String serializar(Document doc) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
