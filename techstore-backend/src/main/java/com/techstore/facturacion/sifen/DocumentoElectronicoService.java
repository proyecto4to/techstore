package com.techstore.facturacion.sifen;

import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.RucParaguayo;
import com.techstore.comun.TipoIdentificacion;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import com.techstore.facturacion.Factura;
import com.techstore.usuarios.Usuario;
import com.techstore.ventas.PedidoDetalle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Genera el XML del Documento Electrónico (DE) de una factura según el
 * Manual Técnico del SIFEN v150 (implementación demostrativa del estándar:
 * grupos principales de una Factura Electrónica de venta al contado).
 */
@Service
@RequiredArgsConstructor
public class DocumentoElectronicoService {

    private static final String NS = "http://ekuatia.set.gov.py/sifen/xsd";
    private static final String TIPO_DOC_FE = "01"; // Factura electrónica
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EmpresaConfigService empresaConfig;
    private final SifenProperties sifenProperties;

    /** Datos vigentes del emisor (editables desde la app). */
    private EmpresaProperties empresa() {
        return empresaConfig.actual();
    }

    /** Resultado: el CDC calculado y el XML del documento electrónico. */
    public record DocumentoElectronico(String cdc, String xml) {
    }

    public DocumentoElectronico generar(Factura factura) {
        sifenProperties.exigirActivo();
        try {
            return construir(factura);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("sifen.documento.fallo", ex.getMessage());
        }
    }

    private DocumentoElectronico construir(Factura factura) throws Exception {
        // Salen del número de la factura y no de la configuración: con varios
        // puntos de expedición, el emisor ya no tiene "un" punto, y el CDC tiene
        // que coincidir con el número que está impreso en el documento.
        String[] partes = factura.getNumero().split("-");
        String establecimiento = partes[0];
        String puntoExpedicion = partes[1];
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
        hijo(doc, rDE, "dVerFor", sifenProperties.getVersion().trim());

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
        // El timbrado congelado en la factura: el XML de un documento ya emitido
        // no puede cambiar porque después se haya habilitado otro timbrado.
        hijo(doc, gTimb, "dNumTim", factura.timbradoO(empresa().timbrado()));
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
        // La condición sale de la factura: los códigos del SIFEN para contado y
        // crédito son los mismos 1 y 2 de la Tabla 2 de la DNIT.
        hijo(doc, gCamCond, "iCondOpe", String.valueOf(factura.getCondicionVenta().codigo()));
        hijo(doc, gCamCond, "dDCondOpe", factura.getCondicionVenta().etiqueta());
        Element gPaConEIni = hijo(doc, gCamCond, "gPaConEIni", null);
        hijo(doc, gPaConEIni, "iTiPago", "1");
        hijo(doc, gPaConEIni, "dDesTiPag", "Efectivo");
        hijo(doc, gPaConEIni, "dMonTiPag", plano(factura.getTotal()));
        hijo(doc, gPaConEIni, "cMoneTiPag", factura.getMoneda().codigo());
        hijo(doc, gPaConEIni, "dDMoneTiPag", factura.getMoneda().nombre());

        for (PedidoDetalle detalle : factura.getPedido().getDetalles()) {
            agregarItem(doc, gDtip, detalle);
        }
        agregarItemEnvio(doc, gDtip, factura.getPedido());

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
        // D129 es obligatorio en el XSD v150 y debe ir antes de gActEco.
        hijo(doc, g, "dEmailE", empresa().email());
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
            // El pedido de una venta directa pertenece al cajero. Para un receptor
            // innominado no se puede copiar su identidad al XML: la factura ya dejó
            // congelado que el receptor es SinNombre.
            hijo(doc, g, "dNomRec", nombreReceptor(factura));
        } else {
            // Con RUC: contribuyente (B2B). El RUC va sin el dígito verificador,
            // que se informa aparte en dDVRec.
            hijo(doc, g, "iNatRec", "1");
            hijo(doc, g, "iTiOpe", "1");
            hijo(doc, g, "cPaisRec", "PRY");
            hijo(doc, g, "dDesPaisRe", "Paraguay");
            hijo(doc, g, "dRucRec", RucParaguayo.numero(ruc));
            hijo(doc, g, "dDVRec", String.valueOf(RucParaguayo.dv(ruc)));
            hijo(doc, g, "dNomRec", nombreReceptor(factura));
        }
        // Opcionales (D213, D214): solo van si se informaron. El manual los da
        // como ocurrencia 0-1 y un elemento vacío sería peor que su ausencia.
        if (factura.getReceptorDireccion() != null) {
            hijo(doc, g, "dDirRec", factura.getReceptorDireccion());
        }
        if (factura.getReceptorTelefono() != null) {
            hijo(doc, g, "dTelRec", factura.getReceptorTelefono());
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
        // E722: "multiplicación del precio por ítem y la cantidad por ítem". Es el
        // bruto, antes del descuento; el neto va más abajo en dTotOpeItem.
        hijo(doc, gVal, "dTotBruOpeItem", plano(detalle.getPrecioUnitario().por(detalle.getCantidad())));

        Element gResta = hijo(doc, gVal, "gValorRestaItem", null);
        Dinero descuento = detalle.getDescuento();
        // EA002 va siempre: el manual pide completar con 0 cuando no hay descuento.
        hijo(doc, gResta, "dDescItem", plano(descuento));
        if (descuento.esPositivo()) {
            // EA003 es obligatorio cuando hay descuento, y su fórmula la fija el
            // manual: EA002 × 100 / E721.
            hijo(doc, gResta, "dPorcDesIt", porcentajeDescuento(detalle));
        }
        // EA008 = (precio − descuento) × cantidad, que es exactamente el subtotal
        // que la línea ya tiene calculado.
        hijo(doc, gResta, "dTotOpeItem", plano(subtotal));

        Element gIVA = hijo(doc, g, "gCamIVA", null);
        // El nombre del campo oficial es iAfecIVA (sin la segunda "t").
        hijo(doc, gIVA, "iAfecIVA", tasa.signum() > 0 ? "1" : "3");
        hijo(doc, gIVA, "dDesAfecIVA", tasa.signum() > 0 ? "Gravado IVA" : "Exento");
        hijo(doc, gIVA, "dPropIVA", "100");
        hijo(doc, gIVA, "dTasaIVA", tasa.stripTrailingZeros().toPlainString());
        hijo(doc, gIVA, "dBasGravIVA", tasa.signum() > 0 ? plano(baseGravada) : "0");
        hijo(doc, gIVA, "dLiqIVAItem", plano(iva));
        // E736 es obligatorio incluso para un ítem gravado: cero si no es
        // exento, o el total de la línea cuando iAfecIVA=3.
        hijo(doc, gIVA, "dBasExe", tasa.signum() > 0 ? "0" : plano(subtotal));
    }

    /** El flete cobrado es un servicio gravado y debe cerrar con el total pagado. */
    private void agregarItemEnvio(Document doc, Element gDtip, com.techstore.ventas.Pedido pedido) {
        Dinero subtotal = pedido.getCostoEnvio();
        if (!subtotal.esPositivo()) return;
        BigDecimal tasa = pedido.getIvaEnvio() == null ? BigDecimal.ZERO : pedido.getIvaEnvio();
        Dinero iva = subtotal.ivaIncluido(tasa);

        Element g = hijo(doc, gDtip, "gCamItem", null);
        hijo(doc, g, "dCodInt", "ENVIO");
        hijo(doc, g, "dDesProSer", "Servicio de entrega");
        hijo(doc, g, "cUniMed", "77");
        hijo(doc, g, "dDesUniMed", "UNI");
        hijo(doc, g, "dCantProSer", "1");
        Element gVal = hijo(doc, g, "gValorItem", null);
        hijo(doc, gVal, "dPUniProSer", plano(subtotal));
        hijo(doc, gVal, "dTotBruOpeItem", plano(subtotal));
        Element gResta = hijo(doc, gVal, "gValorRestaItem", null);
        hijo(doc, gResta, "dDescItem", "0");
        hijo(doc, gResta, "dTotOpeItem", plano(subtotal));
        Element gIva = hijo(doc, g, "gCamIVA", null);
        hijo(doc, gIva, "iAfecIVA", tasa.signum() > 0 ? "1" : "3");
        hijo(doc, gIva, "dDesAfecIVA", tasa.signum() > 0 ? "Gravado IVA" : "Exento");
        hijo(doc, gIva, "dPropIVA", "100");
        hijo(doc, gIva, "dTasaIVA", tasa.stripTrailingZeros().toPlainString());
        hijo(doc, gIva, "dBasGravIVA", tasa.signum() > 0 ? plano(subtotal.menos(iva)) : "0");
        hijo(doc, gIva, "dLiqIVAItem", plano(iva));
        hijo(doc, gIva, "dBasExe", tasa.signum() > 0 ? "0" : plano(subtotal));
    }

    private void agregarTotales(Document doc, Element de, Factura factura) {
        Dinero grav10 = Dinero.cero(factura.getMoneda());
        Dinero grav5 = Dinero.cero(factura.getMoneda());
        Dinero exentas = Dinero.cero(factura.getMoneda());
        Dinero totalDescuento = Dinero.cero(factura.getMoneda());
        for (PedidoDetalle d : factura.getPedido().getDetalles()) {
            int tasa = d.getIvaAplicado() == null ? 0 : d.getIvaAplicado().intValue();
            Dinero monto = d.getSubtotal();
            switch (tasa) {
                case 10 -> grav10 = grav10.mas(monto);
                case 5 -> grav5 = grav5.mas(monto);
                default -> exentas = exentas.mas(monto);
            }
            // Por línea, no por unidad: el descuento se guarda unitario (EA002),
            // pero acá se suma lo que efectivamente se rebajó, que es lo único
            // que hace cerrar bruto − descuento = neto.
            totalDescuento = totalDescuento.mas(d.getDescuentoDeLaLinea());
        }
        Dinero costoEnvio = factura.getPedido().getCostoEnvio();
        int tasaEnvio = factura.getPedido().getIvaEnvio() == null ? 0
                : factura.getPedido().getIvaEnvio().intValue();
        switch (tasaEnvio) {
            case 10 -> grav10 = grav10.mas(costoEnvio);
            case 5 -> grav5 = grav5.mas(costoEnvio);
            default -> exentas = exentas.mas(costoEnvio);
        }
        // El mismo cálculo que guarda la factura y que imprime el KuDE.
        Dinero iva10 = grav10.ivaIncluido(BigDecimal.TEN);
        Dinero iva5 = grav5.ivaIncluido(BigDecimal.valueOf(5));

        Element g = hijo(doc, de, "gTotSub", null);
        hijo(doc, g, "dSubExe", plano(exentas));
        hijo(doc, g, "dSub5", plano(grav5));
        hijo(doc, g, "dSub10", plano(grav10));
        // El XSD v150 define una secuencia estricta. Los conceptos que esta app
        // todavía no utiliza deben informarse con cero, no omitirse: son 1-1.
        hijo(doc, g, "dTotOpe", plano(factura.getTotal()));
        hijo(doc, g, "dTotDesc", plano(totalDescuento));
        hijo(doc, g, "dTotDescGlotem", "0");
        hijo(doc, g, "dTotAntItem", "0");
        hijo(doc, g, "dTotAnt", "0");
        hijo(doc, g, "dPorcDescTotal", "0");
        hijo(doc, g, "dDescTotal", "0");
        hijo(doc, g, "dAnticipo", "0");
        hijo(doc, g, "dRedon", "0");
        hijo(doc, g, "dTotGralOpe", plano(factura.getTotal()));
        hijo(doc, g, "dIVA5", plano(iva5));
        hijo(doc, g, "dIVA10", plano(iva10));
        hijo(doc, g, "dTotIVA", plano(iva5.mas(iva10)));
        Dinero base5 = grav5.menos(iva5);
        Dinero base10 = grav10.menos(iva10);
        hijo(doc, g, "dBaseGrav5", plano(base5));
        hijo(doc, g, "dBaseGrav10", plano(base10));
        hijo(doc, g, "dTBasGraIVA", plano(base5.mas(base10)));
    }

    private static String nombreReceptor(Factura factura) {
        if (factura.getReceptorNombre() != null && !factura.getReceptorNombre().isBlank()) {
            return factura.getReceptorNombre().trim();
        }
        TipoIdentificacion tipo = factura.getReceptorTipoDocumento();
        return tipo == null ? TipoIdentificacion.SinNombre.etiqueta() : tipo.etiqueta();
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

    /**
     * Porcentaje que representa el descuento sobre el precio unitario (EA003).
     * La fórmula la fija el manual: {@code EA002 × 100 / E721}. Se calcula con
     * decimales porque un descuento de 5.000 sobre 90.000 no da un entero.
     */
    private static String porcentajeDescuento(PedidoDetalle detalle) {
        return detalle.getDescuento().monto()
                .multiply(BigDecimal.valueOf(100))
                .divide(detalle.getPrecioUnitario().monto(), 4, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
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
