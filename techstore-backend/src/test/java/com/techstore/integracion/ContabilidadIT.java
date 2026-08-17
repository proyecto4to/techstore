package com.techstore.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El circuito contable contra PostgreSQL de verdad: vender, comprar, cargar el
 * comprobante del proveedor, y que los dos libros y el archivo de la DNIT
 * salgan de ahí.
 *
 * <p>Existe porque los unitarios de contabilidad simulan el repositorio, y hay
 * una clase entera de errores que solo aparece contra la base: el primer intento
 * de armar el libro de ventas falló en producción con
 * {@code type "estadofactura" does not exist}, porque un literal de enum dentro
 * del JPQL se traduce al nombre de la clase Java y no al del tipo de PostgreSQL.
 * Ningún test con mocks podía verlo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Libros de IVA y archivo de la DNIT, contra la base real")
class ContabilidadIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        BaseDatosDePrueba.registrar(registro);
    }

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@techstore.py","password":"admin123"}"""))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static String hoy() {
        return LocalDate.now().toString();
    }

    @Test
    @DisplayName("una venta aparece en el libro con su IVA desglosado")
    void laVentaEntraEnElLibro() throws Exception {
        String jwt = token();

        long productoId = crearProducto(jwt, "Monitor IT contabilidad", "100000.00", 10);
        vender(jwt, productoId, 2, "200000.00");

        MvcResult res = mvc.perform(get("/api/v1/contabilidad/libro-ventas")
                        .header("Authorization", "Bearer " + jwt)
                        .param("desde", hoy())
                        .param("hasta", hoy()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode libro = json.readTree(res.getResponse().getContentAsString());

        assertTrue(libro.get("lineas").size() >= 1, "La venta no aparece en el libro");
        JsonNode totales = libro.get("totales");
        // 200.000 al 10% contienen 18.182 de IVA: el impuesto va adentro del precio.
        assertTrue(totales.get("gravado10").decimalValue().signum() > 0);
        assertTrue(totales.get("iva10").decimalValue().signum() > 0);

        JsonNode linea = ultimaLinea(libro);
        assertEquals(109, linea.get("tipoComprobante").asInt(), "Una factura es el tipo 109");
        assertEquals("Contado", linea.get("condicion").asText());
        assertTrue(linea.get("timbrado").asText().length() > 0,
                "La factura tiene que salir con su timbrado congelado");
    }

    @Test
    @DisplayName("con Desde = Hasta el libro de ventas sirve de cierre diario")
    void elCierreDiarioEsElLibroDeUnDia() throws Exception {
        String jwt = token();

        MvcResult res = mvc.perform(get("/api/v1/contabilidad/libro-ventas")
                        .header("Authorization", "Bearer " + jwt)
                        .param("desde", hoy())
                        .param("hasta", hoy()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode libro = json.readTree(res.getResponse().getContentAsString());

        assertEquals(hoy(), libro.get("desde").asText());
        assertEquals(hoy(), libro.get("hasta").asText());
        assertEquals("Ventas", libro.get("libro").asText());
    }

    @Test
    @DisplayName("la compra entra al libro recién cuando se carga el comprobante del proveedor")
    void laCompraEntraConSuComprobante() throws Exception {
        String jwt = token();

        long proveedorId = crearProveedor(jwt);
        long productoId = crearProducto(jwt, "Teclado IT contabilidad", "80000.00", 5);
        long compraId = crearCompra(jwt, proveedorId, productoId);

        // Sin comprobante, la compra no está en el libro: lo que se declara es el
        // documento del proveedor, no el pedido interno.
        assertEquals(0, comprobantesDelLibroDeCompras(jwt),
                "Una compra sin comprobante no puede estar en el libro");

        mvc.perform(put("/api/v1/compras/" + compraId + "/comprobante")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoComprobante":109,"timbrado":"81225648","numero":"1-1-250",
                                 "fechaEmision":"%s","condicion":"Contado"}""".formatted(hoy())))
                .andExpect(status().isOk());

        assertEquals(1, comprobantesDelLibroDeCompras(jwt));
    }

    @Test
    @DisplayName("el mismo comprobante del proveedor no se puede cargar dos veces")
    void elComprobanteDuplicadoSeRechaza() throws Exception {
        String jwt = token();

        long proveedorId = crearProveedor(jwt);
        long productoId = crearProducto(jwt, "Mouse IT contabilidad", "40000.00", 5);
        long primera = crearCompra(jwt, proveedorId, productoId);
        long segunda = crearCompra(jwt, proveedorId, productoId);

        String cuerpo = """
                {"tipoComprobante":109,"timbrado":"81225649","numero":"001-001-0000900",
                 "fechaEmision":"%s","condicion":"Contado"}""".formatted(hoy());

        mvc.perform(put("/api/v1/compras/" + primera + "/comprobante")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());

        // Cargarlo de nuevo en otra compra duplicaría el crédito fiscal.
        mvc.perform(put("/api/v1/compras/" + segunda + "/comprobante")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("la liquidación del IVA cruza los dos libros")
    void laLiquidacionCruzaLosDosLibros() throws Exception {
        String jwt = token();

        MvcResult res = mvc.perform(get("/api/v1/contabilidad/resumen-iva")
                        .header("Authorization", "Bearer " + jwt)
                        .param("desde", hoy())
                        .param("hasta", hoy()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode resumen = json.readTree(res.getResponse().getContentAsString());

        java.math.BigDecimal debito = resumen.get("debitoFiscal").decimalValue();
        java.math.BigDecimal credito = resumen.get("creditoFiscal").decimalValue();
        assertEquals(0, debito.subtract(credito).compareTo(resumen.get("saldo").decimalValue()),
                "El saldo tiene que ser débito menos crédito");
        assertTrue(resumen.has("situacion"));
    }

    @Test
    @DisplayName("los libros salen en PDF")
    void losLibrosSalenEnPdf() throws Exception {
        String jwt = token();

        for (String url : new String[]{"/api/v1/contabilidad/libro-ventas/pdf",
                "/api/v1/contabilidad/libro-compras/pdf",
                "/api/v1/contabilidad/resumen-iva/pdf"}) {
            MvcResult res = mvc.perform(get(url)
                            .header("Authorization", "Bearer " + jwt)
                            .param("desde", hoy())
                            .param("hasta", hoy()))
                    .andExpect(status().isOk())
                    .andReturn();
            byte[] pdf = res.getResponse().getContentAsByteArray();
            assertTrue(pdf.length > 500, url + " devolvió un PDF sospechosamente chico");
            assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.ISO_8859_1), url);
        }
    }

    @Test
    @DisplayName("el archivo para Marangatu sale como ZIP con el nombre reglamentario")
    void elArchivoDeLaDnitSaleZipeado() throws Exception {
        String jwt = token();

        long productoId = crearProducto(jwt, "Notebook IT contabilidad", "1100000.00", 4);
        vender(jwt, productoId, 1, "1100000.00");

        LocalDate hoy = LocalDate.now();
        MvcResult res = mvc.perform(get("/api/v1/contabilidad/registro-comprobantes")
                        .header("Authorization", "Bearer " + jwt)
                        .param("anio", String.valueOf(hoy.getYear()))
                        .param("mes", String.valueOf(hoy.getMonthValue()))
                        .param("formato", "TXT")
                        .param("identificador", "V0001")
                        .param("renta", "IRE"))
                .andExpect(status().isOk())
                .andReturn();

        String nombre = res.getResponse().getHeader("Content-Disposition");
        assertTrue(nombre != null && nombre.matches(".*\\d+_REG_\\d{6}_V0001\\.zip.*"),
                "El nombre del archivo no sigue el formato de la DNIT: " + nombre);

        byte[] zip = res.getResponse().getContentAsByteArray();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            var item = entrada.getNextEntry();
            assertTrue(item != null && item.getName().endsWith(".txt"), "El ZIP tiene que traer el TXT adentro");
            String contenido = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            String primera = contenido.split("\r\n")[0];
            // 19 campos para una venta, separados por tabulación, y el primero
            // es el código de tipo de registro.
            assertEquals(19, primera.split("\t", -1).length,
                    "La fila de venta no tiene los 19 campos de la especificación");
            assertTrue(primera.startsWith("1\t"), "El tipo de registro de una venta es 1");
        }
    }

    @Test
    @DisplayName("quien no es Admin no entra a contabilidad")
    void soloAdminVeLosLibros() throws Exception {
        // Se registra una cuenta al vuelo en vez de usar la del cajero de
        // demostración: ese usuario no está en las migraciones semilla, así que
        // el test dependería de una base cargada a mano.
        MvcResult res = mvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Sin","apellido":"Permisos",
                                 "email":"sin.permisos.contabilidad@techstore.py",
                                 "password":"Prueba1234"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        String jwt = json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();

        // Autenticado pero sin el rol: 403, no 401. Acá se ve el margen del
        // negocio entero y sale el archivo que se le presenta a la DNIT.
        mvc.perform(get("/api/v1/contabilidad/libro-ventas")
                        .header("Authorization", "Bearer " + jwt)
                        .param("desde", hoy())
                        .param("hasta", hoy()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/contabilidad/registro-comprobantes")
                        .header("Authorization", "Bearer " + jwt)
                        .param("anio", "2026").param("mes", "8"))
                .andExpect(status().isForbidden());
    }

    // --- Ayudantes -----------------------------------------------------------

    private long crearProducto(String jwt, String nombre, String precio, int stock) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"%s","descripcion":"IT contabilidad","precio":%s,"iva":10,
                                 "stockReal":%d,"stockMinimo":0,"marcaId":null,"modeloId":null,
                                 "imagenUrl":null,"activo":true}""".formatted(nombre, precio, stock)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private void vender(String jwt, long productoId, int cantidad, String monto) throws Exception {
        mvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":%d}],
                                 "lineas":[{"tipoPago":"Efectivo","monto":%s,"numeroComprobante":null}],
                                 "receptor":null,"notas":"IT contabilidad"}"""
                                .formatted(productoId, cantidad, monto)))
                .andExpect(status().isCreated());
    }

    private long crearProveedor(String jwt) throws Exception {
        // RUC con dígito verificador válido: el comprobante lo exige.
        String ruc = "8001111%d-%s".formatted(1, "7");
        MvcResult res = mvc.perform(post("/api/v1/proveedores")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"razonSocial":"Proveedor IT contabilidad","ruc":"%s",
                                 "telefono":null,"email":null}""".formatted(ruc)))
                .andReturn();
        if (res.getResponse().getStatus() == 201) {
            return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
        }
        // Ya existía de otro test de esta misma clase: se reutiliza.
        MvcResult lista = mvc.perform(get("/api/v1/proveedores/activos")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode p : json.readTree(lista.getResponse().getContentAsString())) {
            if (ruc.equals(p.path("ruc").asText())) {
                return p.get("id").asLong();
            }
        }
        throw new IllegalStateException("No se pudo crear ni encontrar el proveedor de prueba");
    }

    private long crearCompra(String jwt, long proveedorId, long productoId) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/compras")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proveedorId":%d,"items":[{"productoId":%d,"cantidad":1,
                                 "costoUnitario":50000.00}],"notas":"IT contabilidad"}"""
                                .formatted(proveedorId, productoId)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private int comprobantesDelLibroDeCompras(String jwt) throws Exception {
        MvcResult res = mvc.perform(get("/api/v1/contabilidad/libro-compras")
                        .header("Authorization", "Bearer " + jwt)
                        .param("desde", hoy())
                        .param("hasta", hoy()))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("totales").get("comprobantes").asInt();
    }

    private static JsonNode ultimaLinea(JsonNode libro) {
        JsonNode lineas = libro.get("lineas");
        return lineas.get(lineas.size() - 1);
    }
}
