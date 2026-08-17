package com.techstore.contabilidad;

import com.techstore.comun.BusinessException;
import com.techstore.comun.CondicionOperacion;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.TipoComprobante;
import com.techstore.comun.TipoIdentificacion;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * El archivo del Registro de Comprobantes se compara campo por campo contra la
 * especificación técnica de la DNIT (junio 2021), incluido el ejemplo que trae
 * el propio documento.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Archivo del Registro de Comprobantes (RG 90/2021)")
class RegistroComprobantesServiceTest {

    private static final Moneda PYG = Moneda.PYG;

    @Mock
    private LibroIvaService libroIvaService;

    @Mock
    private EmpresaConfigService empresaConfig;

    private RegistroComprobantesService servicio;

    @BeforeEach
    void configurar() {
        servicio = new RegistroComprobantesService(libroIvaService, empresaConfig);
        when(empresaConfig.actual()).thenReturn(emisor());
        when(libroIvaService.lineasDeVentas(any(), any(), any())).thenReturn(List.of());
        when(libroIvaService.lineasDeCompras(any(), any(), any())).thenReturn(List.of());
    }

    private static EmpresaProperties emisor() {
        return new EmpresaProperties(
                "TechStore Paraguay", "80024627-1", "Avda. España 1234", "Asunción",
                "(021) 555-123", "facturacion@techstore.py", "Venta de equipos", "11138251",
                "01/01/2026 al 31/12/2026",
                2, "001", "001", "47411", "11", "CENTRAL", "1", "ASUNCION", "1", "ASUNCION",
                "2026-01-01", "0");
    }

    private static LineaLibro factura(long gravado10) {
        return new LineaLibro(
                LocalDate.of(2021, 5, 5), TipoComprobante.Factura, "11138251", "001-002-0000250",
                TipoIdentificacion.RUC, "80024627-1", "MINISTERIO DE HACIENDA",
                CondicionOperacion.Contado,
                Dinero.de(gravado10, PYG), Dinero.cero(PYG), Dinero.cero(PYG),
                Dinero.de(gravado10, PYG), null, null);
    }

    @Test
    @DisplayName("una factura de venta sale con los 19 campos del anexo oficial")
    void filaDeVentaComoElEjemploOficial() throws IOException {
        when(libroIvaService.lineasDeVentas(any(), any(), any()))
                .thenReturn(List.of(factura(3_630_000)));

        String fila = unicaFila(servicio.generar(2021, 5, FormatoArchivo.CSV, "V0001", ImpuestoALaRenta.IRE));

        // Es el modelo del anexo de la especificación, salvo el campo 16: el
        // ejemplo lo trae en N porque no imputa a ninguna renta, y acá se está
        // exportando imputando al IRE.
        assertEquals(
                "1;11;80024627;MINISTERIO DE HACIENDA;109;05/05/2021;11138251;001-002-0000250"
                        + ";3630000;0;0;3630000;1;N;S;S;N;;",
                fila);
    }

    @Test
    @DisplayName("el RUC va sin dígito verificador")
    void rucSinDigitoVerificador() throws IOException {
        when(libroIvaService.lineasDeVentas(any(), any(), any()))
                .thenReturn(List.of(factura(1_100_000)));

        String[] campos = unicaFila(servicio.generar(2021, 5, FormatoArchivo.CSV, "V0001", ImpuestoALaRenta.IRE))
                .split(";", -1);

        assertEquals("11", campos[1]);
        assertEquals("80024627", campos[2]);
    }

    @Test
    @DisplayName("una nota de crédito va con importes positivos y con el documento que corrige")
    void notaDeCreditoDeclaraElAsociado() throws IOException {
        LineaLibro nota = new LineaLibro(
                LocalDate.of(2026, 3, 10), TipoComprobante.NotaCredito, "11138251", "001-001-0000012",
                TipoIdentificacion.RUC, "80024627-1", "MINISTERIO DE HACIENDA",
                CondicionOperacion.Contado,
                Dinero.de(550_000, PYG), Dinero.cero(PYG), Dinero.cero(PYG),
                Dinero.de(550_000, PYG), "001-001-0000009", "11138251");
        when(libroIvaService.lineasDeVentas(any(), any(), any())).thenReturn(List.of(nota));

        String[] campos = unicaFila(servicio.generar(2026, 3, FormatoArchivo.CSV, "V0001", ImpuestoALaRenta.IRE))
                .split(";", -1);

        assertEquals("110", campos[4]);
        // El signo lo lleva el tipo de comprobante: los montos van positivos.
        assertEquals("550000", campos[11]);
        assertEquals("001-001-0000009", campos[17]);
        assertEquals("11138251", campos[18]);
    }

    @Test
    @DisplayName("la compra lleva veinte campos: el que sobra es NO IMPUTA")
    void laCompraTieneUnCampoMas() throws IOException {
        LineaLibro compra = new LineaLibro(
                LocalDate.of(2026, 3, 4), TipoComprobante.Factura, "81225648", "001-001-0000777",
                TipoIdentificacion.RUC, "80011111-7", "IMPORTADORA S.A.",
                CondicionOperacion.Credito,
                Dinero.de(1_100_000, PYG), Dinero.de(210_000, PYG), Dinero.de(50_000, PYG),
                Dinero.de(1_360_000, PYG), null, null);
        when(libroIvaService.lineasDeCompras(any(), any(), any())).thenReturn(List.of(compra));

        String[] campos = unicaFila(servicio.generar(2026, 3, FormatoArchivo.CSV, "V0001", ImpuestoALaRenta.IRE))
                .split(";", -1);

        assertEquals(20, campos.length);
        assertEquals("2", campos[0]);
        assertEquals("2", campos[12], "condición de compra: crédito");
        assertEquals("S", campos[14], "imputa al IVA");
        assertEquals("N", campos[17], "no imputa");
    }

    @Test
    @DisplayName("una compra que no da crédito fiscal no se imputa al IVA")
    void elTicketNoSeImputaAlIva() throws IOException {
        LineaLibro ticket = new LineaLibro(
                LocalDate.of(2026, 3, 4), TipoComprobante.TicketMaquinaRegistradora, "81225648", null,
                TipoIdentificacion.RUC, "80011111-7", "SUPERMERCADO S.A.",
                CondicionOperacion.Contado,
                Dinero.cero(PYG), Dinero.cero(PYG), Dinero.cero(PYG),
                Dinero.de(45_000, PYG), null, null);
        when(libroIvaService.lineasDeCompras(any(), any(), any())).thenReturn(List.of(ticket));

        String[] campos = unicaFila(servicio.generar(2026, 3, FormatoArchivo.CSV, "V0001", ImpuestoALaRenta.IRE))
                .split(";", -1);

        assertEquals("N", campos[14], "un ticket no da crédito fiscal");
        assertEquals("", campos[7], "el ticket no lleva número");
        assertEquals("45000", campos[11]);
    }

    @Test
    @DisplayName("el despacho de importación se declara con timbrado cero")
    void despachoDeImportacionConTimbradoCero() throws IOException {
        LineaLibro despacho = new LineaLibro(
                LocalDate.of(2026, 3, 4), TipoComprobante.DespachoImportacion, null, null,
                TipoIdentificacion.IdentificacionTributaria, "AR30500001735", "PROVEEDOR DEL EXTERIOR",
                CondicionOperacion.Contado,
                Dinero.de(5_000_000, PYG), Dinero.cero(PYG), Dinero.cero(PYG),
                Dinero.de(5_000_000, PYG), null, null);
        when(libroIvaService.lineasDeCompras(any(), any(), any())).thenReturn(List.of(despacho));

        String[] campos = unicaFila(servicio.generar(2026, 3, FormatoArchivo.CSV, "V0001", ImpuestoALaRenta.IRE))
                .split(";", -1);

        assertEquals("0", campos[6]);
        // Con identificación tributaria del exterior sí hace falta el nombre.
        assertEquals("PROVEEDOR DEL EXTERIOR", campos[3]);
        assertEquals("AR30500001735", campos[2]);
    }

    @Test
    @DisplayName("el separador que aparezca en una razón social no corre las columnas")
    void unaRazonSocialConPuntoYComaNoRompeLaFila() throws IOException {
        LineaLibro compra = new LineaLibro(
                LocalDate.of(2026, 3, 4), TipoComprobante.Factura, "81225648", "001-001-0000777",
                TipoIdentificacion.IdentificacionTributaria, "AR30500001735", "EMPRESA; S.A.",
                CondicionOperacion.Contado,
                Dinero.de(100_000, PYG), Dinero.cero(PYG), Dinero.cero(PYG),
                Dinero.de(100_000, PYG), null, null);
        when(libroIvaService.lineasDeCompras(any(), any(), any())).thenReturn(List.of(compra));

        String[] campos = unicaFila(servicio.generar(2026, 3, FormatoArchivo.CSV, "V0001", ImpuestoALaRenta.IRE))
                .split(";", -1);

        assertEquals(20, campos.length);
        assertEquals("EMPRESA S.A.", campos[3]);
    }

    @Test
    @DisplayName("el nombre del archivo lleva el RUC sin dígito verificador y el período")
    void nombreDelArchivoMensual() {
        when(libroIvaService.lineasDeVentas(any(), any(), any()))
                .thenReturn(List.of(factura(1_000_000)));

        RegistroComprobantesService.Archivo mensual =
                servicio.generar(2026, 8, FormatoArchivo.TXT, "V0001", ImpuestoALaRenta.IRE);

        assertEquals("80024627_REG_082026_V0001.zip", mensual.nombre());
    }

    @Test
    @DisplayName("el registro anual usa solo el año")
    void nombreDelArchivoAnual() {
        when(libroIvaService.lineasDeVentas(any(), any(), any()))
                .thenReturn(List.of(factura(1_000_000)));

        RegistroComprobantesService.Archivo anual =
                servicio.generar(2026, null, FormatoArchivo.TXT, "E0001", ImpuestoALaRenta.IRE);

        assertEquals("80024627_REG_2026_E0001.zip", anual.nombre());
    }

    @Test
    @DisplayName("el ZIP contiene un archivo con el mismo nombre, como pide la especificación")
    void elZipTieneUnSoloArchivoConSuMismoNombre() throws IOException {
        when(libroIvaService.lineasDeVentas(any(), any(), any()))
                .thenReturn(List.of(factura(1_000_000)));

        RegistroComprobantesService.Archivo archivo =
                servicio.generar(2026, 8, FormatoArchivo.TXT, "V0001", ImpuestoALaRenta.IRE);

        List<String> nombres = nombresDeEntradas(archivo.contenido());
        assertEquals(List.of("80024627_REG_082026_V0001.txt"), nombres);
    }

    @Test
    @DisplayName("el TXT separa con tabulaciones")
    void elTxtVaConTabulaciones() throws IOException {
        when(libroIvaService.lineasDeVentas(any(), any(), any()))
                .thenReturn(List.of(factura(1_000_000)));

        String fila = unicaFila(servicio.generar(2026, 8, FormatoArchivo.TXT, "V0001", ImpuestoALaRenta.IRE));

        assertEquals(19, fila.split("\t", -1).length);
        assertTrue(fila.startsWith("1\t11\t80024627\t"));
    }

    @Test
    @DisplayName("no genera nada si al período le falta un dato obligatorio")
    void nogeneraConPeriodoIncompleto() {
        // Marangatu rechaza el archivo entero, no la fila: dejarlo pasar sería
        // hacerle perder el viaje a quien lo sube.
        when(libroIvaService.lineasDeVentas(any(), any(), any())).thenAnswer(invocacion -> {
            List<String> avisos = invocacion.getArgument(2);
            avisos.add("Factura 001-001-0000003: no tiene timbrado registrado y la DNIT lo exige.");
            return List.of(factura(1_000_000));
        });

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.generar(2026, 8, FormatoArchivo.TXT, "V0001", ImpuestoALaRenta.IRE));

        assertEquals("contabilidad.periodo.incompleto", error.getClave());
    }

    @Test
    @DisplayName("un período sin comprobantes no produce un archivo vacío")
    void periodoSinComprobantes() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.generar(2026, 8, FormatoArchivo.TXT, "V0001", ImpuestoALaRenta.IRE));

        assertEquals("contabilidad.periodo.sin.comprobantes", error.getClave());
    }

    @Test
    @DisplayName("rechaza un identificador que no entre en las cinco posiciones")
    void identificadorInvalido() {
        when(libroIvaService.lineasDeVentas(any(), any(), any()))
                .thenReturn(List.of(factura(1_000_000)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.generar(2026, 8, FormatoArchivo.TXT, "V-0001", ImpuestoALaRenta.IRE));

        assertEquals("contabilidad.identificador.formato", error.getClave());
    }

    @Test
    @DisplayName("el mes decide el rango que se le pide al libro")
    void elRangoEsElMesCompleto() {
        when(libroIvaService.lineasDeVentas(any(), any(), any()))
                .thenReturn(List.of(factura(1_000_000)));

        servicio.generar(2026, 2, FormatoArchivo.TXT, "V0001", ImpuestoALaRenta.IRE);

        // Febrero de 2026 termina el 28: el último día sale del calendario, no
        // de un 30 fijo.
        org.mockito.Mockito.verify(libroIvaService).lineasDeVentas(
                eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(2026, 2, 28)), any());
    }

    @Test
    @DisplayName("imputar al IRP en vez de al IRE cambia los campos 16 y 17")
    void imputacionAlIrp() throws IOException {
        when(libroIvaService.lineasDeVentas(any(), any(), any()))
                .thenReturn(List.of(factura(1_000_000)));

        String[] campos = unicaFila(servicio.generar(2026, 8, FormatoArchivo.CSV, "V0001", ImpuestoALaRenta.IRP))
                .split(";", -1);

        assertEquals("N", campos[15], "imputa al IRE");
        assertEquals("S", campos[16], "imputa al IRP-RSP");
    }

    /** Desarma el ZIP y devuelve la única fila que trae adentro. */
    private static String unicaFila(RegistroComprobantesService.Archivo archivo) throws IOException {
        List<String> filas = filas(archivo.contenido());
        assertEquals(1, filas.size(), "se esperaba una sola fila");
        return filas.get(0);
    }

    private static List<String> filas(byte[] zip) throws IOException {
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            entrada.getNextEntry();
            String contenido = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            return List.of(contenido.split("\r\n"));
        }
    }

    private static List<String> nombresDeEntradas(byte[] zip) throws IOException {
        List<String> nombres = new ArrayList<>();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry item;
            while ((item = entrada.getNextEntry()) != null) {
                nombres.add(item.getName());
            }
        }
        return nombres;
    }
}
