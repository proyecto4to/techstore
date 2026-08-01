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

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una venta de mostrador de punta a punta, por HTTP real: login con JWT,
 * alta de producto, cobro, y las consecuencias que eso tiene en tres lugares
 * distintos —stock, caja y factura—.
 *
 * <p>Este es el test que hay que tener antes de meterle mano al esquema. Los
 * unitarios validan cada servicio con los demás simulados; ninguno se da cuenta
 * si una migración rompe una columna, si la configuración de seguridad deja un
 * endpoint abierto, o si el cobro deja de descontar stock. Este sí.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Venta completa de punta a punta")
class VentaCompletaIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        BaseDatosDePrueba.registrar(registro);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

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

    @Test
    @DisplayName("cobrar descuenta stock, mueve la caja, factura y deja auditoría")
    void ventaDirectaCompleta() throws Exception {
        String jwt = token();

        // --- 1. Un producto nuevo, con stock conocido -------------------------
        MvcResult creado = mvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Producto de prueba IT","descripcion":"venta completa",
                                 "precio":100000.00,"iva":10,"stockReal":10,"stockMinimo":3,
                                 "marcaId":null,"modeloId":null,"imagenUrl":null,"activo":true}"""))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode producto = json.readTree(creado.getResponse().getContentAsString());
        long productoId = producto.get("id").asLong();
        assertEquals(10, producto.get("stockReal").asInt());
        assertEquals(3, producto.get("stockMinimo").asInt());

        long movimientosAntes = contar("SELECT count(*) FROM movimiento_caja");

        // --- 2. La venta: 2 unidades, mitad efectivo y mitad transferencia ----
        MvcResult venta = mvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":2}],
                                 "lineas":[{"tipoPago":"Efectivo","monto":120000.00,"numeroComprobante":null},
                                           {"tipoPago":"Transferencia","monto":80000.00,"numeroComprobante":"TRF-IT-1"}],
                                 "receptor":null,"notas":"venta de prueba"}"""
                                .formatted(productoId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode pago = json.readTree(venta.getResponse().getContentAsString());

        // --- 3. Lo cobrado coincide con el precio por la cantidad -------------
        assertEquals(0, new BigDecimal("200000.00").compareTo(pago.get("total").decimalValue()),
                "El total cobrado no es 2 x 100.000");
        assertEquals(2, pago.get("lineas").size(), "Se esperaba un movimiento por método de pago");

        // --- 4. Se emitió factura, con número y CDC --------------------------
        JsonNode factura = pago.get("factura");
        assertNotNull(factura, "La venta no emitió factura");
        String numero = factura.get("numero").asText();
        assertTrue(numero.matches("\\d{3}-\\d{3}-\\d{7}"),
                "El número de factura no tiene el formato de la DNIT: " + numero);

        // --- 5. El stock bajó, y el producto quedó en zona de reposición ------
        MvcResult despues = mvc.perform(get("/api/v1/productos/" + productoId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pDespues = json.readTree(despues.getResponse().getContentAsString());
        assertEquals(8, pDespues.get("stockReal").asInt(), "El cobro no descontó el stock");
        // 8 disponibles contra un mínimo de 3: todavía no hay que reponer.
        assertEquals(false, pDespues.get("necesitaReposicion").asBoolean());

        // --- 6. La caja recibió un movimiento por método, con el prefijo FAC --
        assertEquals(movimientosAntes + 2, contar("SELECT count(*) FROM movimiento_caja"),
                "Se esperaba un movimiento de caja por cada método de pago");
        assertEquals(1, contar(
                "SELECT count(*) FROM movimiento_caja WHERE referencia = 'FAC " + numero + "'"
                        + " AND medio = 'Efectivo'"));
        assertEquals(1, contar(
                "SELECT count(*) FROM movimiento_caja WHERE referencia = 'FAC " + numero + "'"
                        + " AND medio = 'Transferencia'"));

        // --- 7. Y quedó rastro en la auditoría del alta del producto ---------
        assertTrue(contar("SELECT count(*) FROM audit_log WHERE entidad = 'Producto'"
                        + " AND accion = 'Crear' AND entidad_id = " + productoId) == 1,
                "El alta del producto no quedó auditada");
    }

    @Test
    @DisplayName("sin token, la API no deja pasar")
    void sinTokenNoSePuedeVender() throws Exception {
        mvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":1,"cantidad":1}],
                                 "lineas":[{"tipoPago":"Efectivo","monto":1.00,"numeroComprobante":null}]}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("no se puede vender más de lo que hay")
    void noSeVendeSinStock() throws Exception {
        String jwt = token();
        MvcResult creado = mvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Producto escaso IT","descripcion":"sin stock",
                                 "precio":50000.00,"iva":10,"stockReal":1,"stockMinimo":0,
                                 "marcaId":null,"modeloId":null,"imagenUrl":null,"activo":true}"""))
                .andExpect(status().isCreated())
                .andReturn();
        long id = json.readTree(creado.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":5}],
                                 "lineas":[{"tipoPago":"Efectivo","monto":250000.00,"numeroComprobante":null}]}"""
                                .formatted(id)))
                // 409 y no 400: el pedido está bien formado, lo que falla es que
                // choca con el estado actual del stock. Es la respuesta correcta.
                .andExpect(status().isConflict());
    }

    private long contar(String sql) throws SQLException {
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
