package com.techstore.contabilidad;

import com.techstore.comun.CondicionOperacion;
import com.techstore.comun.DesgloseIva;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.TipoComprobante;
import com.techstore.comun.TipoIdentificacion;
import com.techstore.compras.CompraComprobante;
import com.techstore.compras.CompraComprobanteRepository;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.facturacion.Factura;
import com.techstore.facturacion.FacturaRepository;
import com.techstore.facturacion.NotaCredito;
import com.techstore.facturacion.NotaCreditoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Arma los libros de IVA de ventas y de compras a partir de lo que ya está
 * registrado: las facturas y notas de crédito emitidas, y los comprobantes que
 * mandaron los proveedores.
 *
 * <p>No hay tabla de "libro". Un libro de IVA no es un dato nuevo sino una
 * vista de los comprobantes del período, y guardarlo aparte solo agregaría una
 * copia que se puede desincronizar de los documentos que resume.
 *
 * <p>El IVA tampoco se guarda: se extrae de los montos gravados con
 * {@link Dinero#ivaIncluido}. Es el mismo cálculo, con el mismo redondeo, que
 * usan la factura impresa, el KuDE y el documento electrónico.
 *
 * <h2>Qué se declara y qué no</h2>
 * <ul>
 *   <li>Las facturas <b>anuladas</b> quedan afuera: un documento anulado no se
 *       declara, y el hueco en la numeración es correcto.</li>
 *   <li>Las <b>notas de crédito</b> son un comprobante propio (tipo 110) que
 *       resta débito fiscal, no un ajuste sobre la factura original.</li>
 *   <li>Las compras entran por el <b>comprobante del proveedor</b> y por su
 *       fecha de emisión, no por la fecha en que se cargó la compra: el crédito
 *       fiscal corresponde al período del papel.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LibroIvaService {

    public static final String VENTAS = "Ventas";
    public static final String COMPRAS = "Compras";

    /**
     * La DNIT no acepta comprobantes anteriores a esta fecha en el Registro de
     * Comprobantes, salvo que la operación sea a crédito.
     */
    private static final LocalDate LIMITE_DNIT = LocalDate.of(2021, 1, 1);

    private final FacturaRepository facturaRepository;
    private final NotaCreditoRepository notaCreditoRepository;
    private final CompraComprobanteRepository comprobanteRepository;
    private final EmpresaConfigService empresaConfig;

    @Transactional(readOnly = true)
    public LibroIvaResponse ventas(LocalDate desde, LocalDate hasta) {
        List<String> avisos = new ArrayList<>();
        List<LineaLibro> lineas = lineasDeVentas(desde, hasta, avisos);
        return LibroIvaResponse.de(VENTAS, desde, hasta, moneda(), lineas, avisos);
    }

    @Transactional(readOnly = true)
    public LibroIvaResponse compras(LocalDate desde, LocalDate hasta) {
        List<String> avisos = new ArrayList<>();
        List<LineaLibro> lineas = lineasDeCompras(desde, hasta, avisos);
        return LibroIvaResponse.de(COMPRAS, desde, hasta, moneda(), lineas, avisos);
    }

    /**
     * La liquidación del período: lo que se cobró de IVA en las ventas menos lo
     * que se pagó en las compras.
     */
    @Transactional(readOnly = true)
    public ResumenIvaResponse resumen(LocalDate desde, LocalDate hasta) {
        LibroIvaResponse libroVentas = ventas(desde, hasta);
        LibroIvaResponse libroCompras = compras(desde, hasta);
        return ResumenIvaResponse.de(desde, hasta, moneda(), libroVentas, libroCompras);
    }

    /** Las líneas crudas, para el exportador y para el PDF. */
    List<LineaLibro> lineasDeVentas(LocalDate desde, LocalDate hasta, List<String> avisos) {
        Moneda moneda = moneda();
        String sinNombre = identificacionSinNombre();
        List<LineaLibro> lineas = new ArrayList<>();

        for (Factura factura : facturaRepository.delPeriodo(inicio(desde), fin(hasta))) {
            if (factura.getMoneda() != moneda) {
                avisos.add("La factura %s está en %s y el libro se lleva en %s: hay que convertirla antes de declararla."
                        .formatted(factura.getNumero(), factura.getMoneda().codigo(), moneda.codigo()));
                continue;
            }
            DesgloseIva desglose = DesgloseIva.de(moneda, factura.getPedido().getDetalles());
            avisarSiNoCuadra(avisos, "La factura", factura.getNumero(), desglose, factura.getTotal());
            boolean identificado = factura.getReceptorRuc() != null;
            lineas.add(new LineaLibro(
                    factura.getFecha().toLocalDate(),
                    TipoComprobante.Factura,
                    factura.getTimbrado(),
                    factura.getNumero(),
                    identificado ? TipoIdentificacion.RUC : TipoIdentificacion.SinNombre,
                    identificado ? factura.getReceptorRuc() : sinNombre,
                    factura.getReceptorNombre(),
                    factura.getCondicionVenta(),
                    desglose.gravado10(), desglose.gravado5(), desglose.exento(),
                    factura.getTotal(),
                    null, null));
        }

        for (NotaCredito nota : notaCreditoRepository.delPeriodo(inicio(desde), fin(hasta))) {
            Factura factura = nota.getFactura();
            if (nota.getMoneda() != moneda) {
                avisos.add("La nota de crédito %s está en %s y el libro se lleva en %s."
                        .formatted(nota.getNumero(), nota.getMoneda().codigo(), moneda.codigo()));
                continue;
            }
            DesgloseIva desglose = DesgloseIva.de(moneda, nota.getDetalles());
            avisarSiNoCuadra(avisos, "La nota de crédito", nota.getNumero(), desglose, nota.getTotal());
            boolean identificado = factura.getReceptorRuc() != null;
            lineas.add(new LineaLibro(
                    nota.getFecha().toLocalDate(),
                    TipoComprobante.NotaCredito,
                    // La nota de crédito sale del mismo talonario que la factura
                    // que corrige, así que lleva su timbrado.
                    factura.getTimbrado(),
                    nota.getNumero(),
                    identificado ? TipoIdentificacion.RUC : TipoIdentificacion.SinNombre,
                    identificado ? factura.getReceptorRuc() : sinNombre,
                    factura.getReceptorNombre(),
                    factura.getCondicionVenta(),
                    desglose.gravado10(), desglose.gravado5(), desglose.exento(),
                    nota.getTotal(),
                    factura.getNumero(), factura.getTimbrado()));
        }

        lineas.sort(Comparator.comparing(LineaLibro::fecha).thenComparing(LineaLibro::numero));
        revisar(lineas, avisos, "venta");
        return lineas;
    }

    List<LineaLibro> lineasDeCompras(LocalDate desde, LocalDate hasta, List<String> avisos) {
        Moneda moneda = moneda();
        List<LineaLibro> lineas = new ArrayList<>();

        for (CompraComprobante c : comprobanteRepository.delPeriodo(desde, hasta)) {
            if (c.getMoneda() != moneda) {
                avisos.add("El comprobante %s está en %s y el libro se lleva en %s."
                        .formatted(c.getNumero(), c.getMoneda().codigo(), moneda.codigo()));
                continue;
            }
            lineas.add(new LineaLibro(
                    c.getFechaEmision(),
                    c.getTipoComprobante(),
                    c.getTimbrado(),
                    c.getNumero(),
                    c.getTipoIdentificacion(),
                    c.getProveedorRuc(),
                    c.getProveedorRazonSocial(),
                    c.getCondicion(),
                    c.getGravado10(), c.getGravado5(), c.getExento(), c.getTotal(),
                    c.getAsociadoNumero(), c.getAsociadoTimbrado()));
        }

        revisar(lineas, avisos, "compra");
        return lineas;
    }

    /**
     * Avisa cuando el desglose por tasa no da el total del documento.
     *
     * <p>No es un detalle estético: la DNIT valida exactamente eso —el campo del
     * monto total tiene que ser igual a la suma de gravadas al 10%, al 5% y
     * exentas— y rechaza el archivo entero si no cierra.
     *
     * <p>Pasa de verdad. Las notas de crédito anteriores a que el sistema
     * guardara el detalle línea por línea tienen su total pero no tienen ítems,
     * así que no hay con qué abrirlas por tasa. Eso no se puede adivinar sin
     * inventar una alícuota, y por eso se avisa en vez de completar.
     */
    private static void avisarSiNoCuadra(List<String> avisos, String queEs, String numero,
                                         DesgloseIva desglose, Dinero total) {
        if (desglose.total().compareTo(total) == 0) {
            return;
        }
        avisos.add(("%s %s totaliza %s pero sus ítems suman %s: sin el detalle no se puede abrir "
                + "por tasa de IVA, y la DNIT exige que el total sea igual a la suma de las columnas.")
                .formatted(queEs, numero, total.plano(), desglose.total().plano()));
    }

    /**
     * Revisa que cada línea se pueda declarar. No corrige nada: avisa, porque
     * lo que falta lo tiene que completar una persona mirando el documento.
     */
    private static void revisar(List<LineaLibro> lineas, List<String> avisos, String queEs) {
        for (LineaLibro linea : lineas) {
            String cual = linea.tipo().etiqueta() + " " + (linea.numero() == null ? "sin número" : linea.numero());
            if (linea.tipo().exigeTimbrado() && esVacio(linea.timbrado())) {
                avisos.add("%s: no tiene timbrado registrado y la DNIT lo exige.".formatted(cual));
            }
            if (linea.tipo().exigeNumero() && esVacio(linea.numero())) {
                avisos.add("Hay una %s sin número de comprobante.".formatted(queEs));
            }
            if (esVacio(linea.identificacion())) {
                avisos.add(("%s: falta la identificación de la contraparte. Si es una venta a consumidor final, "
                        + "cargá qué número declarar en Admin → Empresa.").formatted(cual));
            }
            if (linea.tipo().exigeAsociado() && esVacio(linea.asociadoNumero())) {
                avisos.add("%s: no dice qué comprobante corrige.".formatted(cual));
            }
            if (linea.fecha().isBefore(LIMITE_DNIT) && linea.condicion() != CondicionOperacion.Credito) {
                avisos.add("%s es del %s: la DNIT no acepta comprobantes al contado anteriores al %s."
                        .formatted(cual, linea.fecha(), LIMITE_DNIT));
            }
        }
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    /**
     * En qué moneda se lleva el libro. Es la del sistema: un libro de IVA se
     * declara en guaraníes, y mezclar monedas en una misma columna daría un
     * total que no significa nada.
     */
    private static Moneda moneda() {
        return Moneda.porDefecto();
    }

    /** Qué declarar como identificación del comprador en las ventas a consumidor final. */
    private String identificacionSinNombre() {
        String configurado = empresaConfig.actual().identificacionSinNombre();
        return esVacio(configurado) ? null : configurado.trim();
    }

    private static LocalDateTime inicio(LocalDate desde) {
        return desde.atStartOfDay();
    }

    /** Exclusivo, para que el rango incluya todo el último día. */
    private static LocalDateTime fin(LocalDate hasta) {
        return hasta.plusDays(1).atStartOfDay();
    }
}
