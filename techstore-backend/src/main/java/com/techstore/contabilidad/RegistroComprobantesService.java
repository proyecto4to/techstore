package com.techstore.contabilidad;

import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Genera el archivo del Registro de Comprobantes que se sube a Marangatu
 * (RG 90/2021), el que reemplazó a Hechauka.
 *
 * <p><b>Qué se declara acá y qué no.</b> La especificación excluye del archivo
 * los comprobantes emitidos por e-Kuatia y los virtuales, porque de esos la
 * DNIT ya tiene el dato. TechStore arma el KuDE y el CDC pero <em>no transmite
 * al SIFEN</em>, así que sus facturas no están del otro lado y sí tienen que ir
 * en el archivo. El día que se transmita de verdad, esto hay que revisarlo: de
 * lo contrario cada venta quedaría declarada dos veces.
 *
 * <p>El nombre del archivo no es decorativo: Marangatu lo usa para saber a qué
 * contribuyente y a qué período corresponde lo que se está subiendo, así que se
 * arma con el RUC sin dígito verificador y el período, tal como pide la
 * especificación.
 */
@Service
@RequiredArgsConstructor
public class RegistroComprobantesService {

    /** Tope por archivo que impone la DNIT. Más que esto va en lotes separados. */
    private static final int FILAS_POR_ARCHIVO = 5_000;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SI = "S";
    private static final String NO = "N";
    private static final int REGISTRO_VENTAS = 1;
    private static final int REGISTRO_COMPRAS = 2;

    private final LibroIvaService libroIvaService;
    private final EmpresaConfigService empresaConfig;

    /** El archivo listo para descargar. */
    public record Archivo(String nombre, byte[] contenido) {
    }

    /**
     * Arma el archivo de un período fiscal.
     *
     * @param mes {@code null} para el registro anual (obligación 956); con mes,
     *            es el registro mensual (obligación 955)
     */
    @Transactional(readOnly = true)
    public Archivo generar(int anio, Integer mes, FormatoArchivo formato,
                           String identificador, ImpuestoALaRenta renta) {
        LocalDate desde = mes == null ? LocalDate.of(anio, 1, 1) : LocalDate.of(anio, mes, 1);
        LocalDate hasta = mes == null
                ? LocalDate.of(anio, 12, 31)
                : desde.withDayOfMonth(desde.lengthOfMonth());

        List<String> avisos = new ArrayList<>();
        List<LineaLibro> ventas = libroIvaService.lineasDeVentas(desde, hasta, avisos);
        List<LineaLibro> compras = libroIvaService.lineasDeCompras(desde, hasta, avisos);
        if (!avisos.isEmpty()) {
            // Un archivo con un campo obligatorio en blanco lo rechaza Marangatu
            // entero, no la fila. Más vale no generarlo y decir qué falta.
            throw new BusinessException("contabilidad.periodo.incompleto",
                    avisos.size(), avisos.get(0));
        }
        if (ventas.isEmpty() && compras.isEmpty()) {
            throw new BusinessException("contabilidad.periodo.sin.comprobantes");
        }

        List<String> filas = new ArrayList<>();
        for (LineaLibro linea : ventas) {
            filas.add(filaDeVenta(linea, formato, renta));
        }
        for (LineaLibro linea : compras) {
            filas.add(filaDeCompra(linea, formato, renta));
        }

        String base = nombreBase(anio, mes);
        List<List<String>> lotes = enLotes(filas);
        if (lotes.size() == 1) {
            String nombre = base + "_" + identificadorDeLote(identificador, 1);
            return new Archivo(nombre + ".zip", zip(nombre + "." + formato.extension(), texto(lotes.get(0))));
        }

        // Cada lote va en su propio ZIP porque así se sube: un archivo por vez.
        // Los ZIP van adentro de otro para que la descarga sea una sola.
        List<Archivo> partes = new ArrayList<>();
        for (int i = 0; i < lotes.size(); i++) {
            String nombre = base + "_" + identificadorDeLote(identificador, i + 1);
            partes.add(new Archivo(nombre + ".zip",
                    zip(nombre + "." + formato.extension(), texto(lotes.get(i)))));
        }
        return new Archivo(base + "_lotes.zip", zipDeVarios(partes));
    }

    /**
     * Fila del registro de ventas: 19 campos en el orden de la especificación.
     */
    private String filaDeVenta(LineaLibro l, FormatoArchivo formato, ImpuestoALaRenta renta) {
        return unir(formato,
                String.valueOf(REGISTRO_VENTAS),
                String.valueOf(l.tipoIdentificacion().codigo()),
                l.identificacionParaDnit(),
                nombre(l),
                String.valueOf(l.tipo().codigo()),
                l.fecha().format(FECHA),
                timbrado(l),
                l.tipo().exigeNumero() ? l.numero() : "",
                entero(l.gravado10()),
                entero(l.gravado5()),
                entero(l.exento()),
                entero(l.total()),
                String.valueOf(l.condicion().codigo()),
                NO,                                   // operación en moneda extranjera
                SI,                                   // imputa al IVA
                renta == ImpuestoALaRenta.IRE ? SI : NO,
                renta == ImpuestoALaRenta.IRP ? SI : NO,
                l.tipo().exigeAsociado() ? l.asociadoNumero() : "",
                l.tipo().exigeAsociado() ? l.asociadoTimbrado() : "");
    }

    /**
     * Fila del registro de compras: 20 campos. La diferencia con ventas es el
     * campo "NO IMPUTA", que va antes de los datos del comprobante asociado.
     */
    private String filaDeCompra(LineaLibro l, FormatoArchivo formato, ImpuestoALaRenta renta) {
        boolean conCredito = l.tipo().llevaDesglose();
        return unir(formato,
                String.valueOf(REGISTRO_COMPRAS),
                String.valueOf(l.tipoIdentificacion().codigo()),
                l.identificacionParaDnit(),
                nombre(l),
                String.valueOf(l.tipo().codigo()),
                l.fecha().format(FECHA),
                timbrado(l),
                l.tipo().exigeNumero() ? l.numero() : "",
                entero(l.gravado10()),
                entero(l.gravado5()),
                entero(l.exento()),
                entero(l.total()),
                String.valueOf(l.condicion().codigo()),
                NO,                                   // operación en moneda extranjera
                // Una compra que no da crédito fiscal —autofactura, ticket— no se
                // imputa al IVA aunque sí al impuesto a la renta.
                conCredito ? SI : NO,
                renta == ImpuestoALaRenta.IRE ? SI : NO,
                renta == ImpuestoALaRenta.IRP ? SI : NO,
                NO,                                   // no imputa
                l.tipo().exigeAsociado() ? l.asociadoNumero() : "",
                l.tipo().exigeAsociado() ? l.asociadoTimbrado() : "");
    }

    /**
     * La razón social de la contraparte.
     *
     * <p>La especificación la exige salvo para RUC, cédula paraguaya y "sin
     * nombre", porque de esos la DNIT ya sabe cómo se llaman. Igual se manda
     * siempre que se la tenga: es lo que hace el ejemplo del propio anexo
     * oficial, no molesta, y hace que el archivo se pueda leer si alguien lo
     * abre para controlarlo antes de subirlo.
     */
    private static String nombre(LineaLibro l) {
        return l.nombre() == null ? "" : l.nombre();
    }

    /** El despacho de importación no tiene timbrado y se declara con cero. */
    private static String timbrado(LineaLibro l) {
        return l.tipo().exigeTimbrado() ? l.timbrado() : "0";
    }

    /** Entero positivo sin decimales ni separadores, como pide la especificación. */
    private static String entero(Dinero importe) {
        return importe.monto().setScale(0, Dinero.REDONDEO).abs().toBigInteger().toString();
    }

    private static String unir(FormatoArchivo formato, String... campos) {
        StringBuilder fila = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) {
                fila.append(formato.separador());
            }
            fila.append(limpiar(campos[i], formato));
        }
        return fila.toString();
    }

    /**
     * Saca del texto cualquier cosa que rompa la fila. Solo puede pasar en la
     * razón social, que es el único campo que escribe una persona: un punto y
     * coma en "EMPRESA, S.A." correría todas las columnas siguientes.
     */
    private static String limpiar(String valor, FormatoArchivo formato) {
        if (valor == null) {
            return "";
        }
        return valor.replace(formato.separador(), " ")
                .replace("\r", " ")
                .replace("\n", " ")
                // Los espacios se colapsan después de sacar el separador: si no,
                // "EMPRESA; S.A." quedaría con un hueco doble en el medio.
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * {@code <RUC sin DV>_REG_MMAAAA} para el registro mensual y
     * {@code <RUC sin DV>_REG_AAAA} para el anual.
     */
    private String nombreBase(int anio, Integer mes) {
        EmpresaProperties emisor = empresaConfig.actual();
        if (emisor.ruc() == null || emisor.ruc().isBlank()) {
            throw new BusinessException("contabilidad.emisor.sin.ruc");
        }
        String periodo = mes == null ? "%04d".formatted(anio) : "%02d%04d".formatted(mes, anio);
        return "%s_REG_%s".formatted(emisor.rucNumero(), periodo);
    }

    /**
     * El identificador de hasta cinco posiciones que la especificación deja
     * elegir al contribuyente, con el número de lote al final: {@code V0001},
     * {@code V0002}. Tiene que ser distinto en cada archivo que se sube.
     */
    private static String identificadorDeLote(String base, int lote) {
        String elegido = base == null || base.isBlank() ? "V0001" : base.trim();
        if (!elegido.matches("[A-Za-z0-9]{1,5}")) {
            throw new BusinessException("contabilidad.identificador.formato", elegido);
        }
        String prefijo = elegido.replaceAll("\\d+$", "");
        if (prefijo.length() > 4) {
            prefijo = prefijo.substring(0, 4);
        }
        String numero = String.valueOf(lote);
        int ancho = Math.max(numero.length(), 5 - prefijo.length());
        return (prefijo + "0".repeat(Math.max(0, ancho - numero.length())) + numero);
    }

    private static List<List<String>> enLotes(List<String> filas) {
        List<List<String>> lotes = new ArrayList<>();
        for (int desde = 0; desde < filas.size(); desde += FILAS_POR_ARCHIVO) {
            lotes.add(filas.subList(desde, Math.min(desde + FILAS_POR_ARCHIVO, filas.size())));
        }
        return lotes;
    }

    /** Sin fila de encabezado: la especificación pide que se excluya. */
    private static byte[] texto(List<String> filas) {
        return String.join("\r\n", filas).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(String nombreEntrada, byte[] contenido) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(nombreEntrada));
            zip.write(contenido);
            zip.closeEntry();
            zip.finish();
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("contabilidad.archivo.fallo", ex.getMessage());
        }
    }

    private static byte[] zipDeVarios(List<Archivo> partes) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            for (Archivo parte : partes) {
                zip.putNextEntry(new ZipEntry(parte.nombre()));
                zip.write(parte.contenido());
                zip.closeEntry();
            }
            zip.finish();
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("contabilidad.archivo.fallo", ex.getMessage());
        }
    }

}
