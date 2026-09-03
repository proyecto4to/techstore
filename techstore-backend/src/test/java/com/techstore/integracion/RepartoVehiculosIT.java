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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Reparto, caja y vehÃ­culos de punta a punta")
class RepartoVehiculosIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void flujoCompletoMantieneIntegridadFinancieraConcurrenciaYPermisos() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        JsonNode courierUser = createUser(admin, "Ruta", "Uno", "ruta.uno@techstore.py", "Reparto123", "Repartidor");
        JsonNode secondCourierUser = createUser(
                admin, "Ruta", "Dos", "ruta.dos@techstore.py", "Reparto123", "Repartidor");
        JsonNode cashierUser = createUser(admin, "Caja", "Ruta", "caja.ruta@techstore.py", "Cajero123", "Cajero");
        register("cliente.ruta@techstore.py");

        String customer = login("cliente.ruta@techstore.py", "Cliente123");
        String courier = login("ruta.uno@techstore.py", "Reparto123");
        String secondCourier = login("ruta.dos@techstore.py", "Reparto123");
        String cashier = login("caja.ruta@techstore.py", "Cajero123");

        long addressId = createAddress(customer);
        long productId = createProduct(admin);
        long vehicleOne = createVehicle(admin, "ITRUTA01", new BigDecimal("1000.0"));
        long vehicleTwo = createVehicle(admin, "ITRUTA02", new BigDecimal("2000.0"));

        long firstOrder = checkout(customer, productId, addressId);
        mvc.perform(get("/api/v1/pedidos/{id}/factura/pdf", firstOrder).header("Authorization", bearer(customer)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/pedidos/{id}/confirmar", firstOrder).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facturaDisponible").value(false));
        long firstAssignment = assign(admin, firstOrder, courierUser.get("id").asLong(), 1);

        mvc.perform(post("/api/v1/vehiculos/{id}/usar", vehicleOne)
                        .header("Authorization", bearer(courier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kilometrajeInicial":1000.0,"combustibleInicial":80,"observacion":"Inicio IT"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/vehiculos/{id}/usar", vehicleOne)
                        .header("Authorization", bearer(secondCourier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kilometrajeInicial":1000.0,"combustibleInicial":70}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/vehiculos/{id}/usar", vehicleTwo)
                        .header("Authorization", bearer(courier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kilometrajeInicial":2000.0,"combustibleInicial":70}
                                """))
                .andExpect(status().isConflict());

        changeDelivery(courier, firstAssignment, "ACEPTADO", null);
        changeDelivery(courier, firstAssignment, "EN_RUTA", null);
        mvc.perform(post("/api/v1/repartos/{id}/cobro", firstAssignment)
                        .header("Authorization", bearer(courier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobroRegistrado").value(true));
        mvc.perform(post("/api/v1/repartos/{id}/cobro", firstAssignment)
                        .header("Authorization", bearer(courier)))
                .andExpect(status().isConflict());
        changeDelivery(courier, firstAssignment, "EN_DESTINO", null);
        changeDelivery(courier, firstAssignment, "ENTREGADO", "Entregado en mano");

        mvc.perform(get("/api/v1/pedidos/{id}", firstOrder).header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facturaDisponible").value(true));
        mvc.perform(get("/api/v1/pedidos/{id}/factura/pdf", firstOrder).header("Authorization", bearer(customer)))
                .andExpect(status().isOk());

        BigDecimal firstBalance = balance(courier);
        assertTrue(firstBalance.signum() > 0);
        long firstTransfer = requestTransfer(courier, cashierUser.get("id").asLong());
        assertEquals(0, firstBalance.compareTo(transferAmount(courier, firstTransfer)));
        mvc.perform(put("/api/v1/repartos/solicitudes/{id}/aceptar", firstTransfer)
                        .header("Authorization", bearer(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACEPTADO"));
        mvc.perform(put("/api/v1/repartos/solicitudes/{id}/aceptar", firstTransfer)
                        .header("Authorization", bearer(cashier)))
                .andExpect(status().isConflict());
        assertEquals(0, BigDecimal.ZERO.compareTo(balance(courier)));

        mvc.perform(post("/api/v1/vehiculos/mi-uso/eventos")
                        .header("Authorization", bearer(courier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"AVERIA","kilometraje":1010.0,
                                 "descripcion":"Falla menor controlada","ubicacion":"AsunciÃ³n",
                                 "datosAdicionales":{"severidad":"MEDIA","puedeContinuar":true}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereAtencion").value(true));
        mvc.perform(post("/api/v1/vehiculos/mi-uso/finalizar")
                        .header("Authorization", bearer(courier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kilometrajeFinal":1009.0,"combustibleFinal":55}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/vehiculos/mi-uso/finalizar")
                        .header("Authorization", bearer(courier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kilometrajeFinal":1011.0,"combustibleFinal":55,"observacion":"Fin IT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"));

        long secondOrder = checkout(customer, productId, addressId);
        mvc.perform(put("/api/v1/pedidos/{id}/confirmar", secondOrder).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        assign(admin, secondOrder, secondCourierUser.get("id").asLong(), 5);
        long secondAssignment = assign(admin, secondOrder, courierUser.get("id").asLong(), 2);
        mvc.perform(get("/api/v1/repartos/pedidos/{id}/asignaciones", secondOrder)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(secondAssignment))
                .andExpect(jsonPath("$[0].repartidorId").value(courierUser.get("id").asLong()))
                .andExpect(jsonPath("$[0].repartidor").value("Ruta Uno"))
                .andExpect(jsonPath("$[1].estadoEntrega").value("CANCELADO"));
        changeDelivery(courier, secondAssignment, "ACEPTADO", null);
        changeDelivery(courier, secondAssignment, "EN_RUTA", null);
        mvc.perform(post("/api/v1/repartos/{id}/cobro", secondAssignment)
                        .header("Authorization", bearer(courier)))
                .andExpect(status().isOk());
        BigDecimal balanceBeforeRejection = balance(courier);
        long rejectedTransfer = requestTransfer(courier, cashierUser.get("id").asLong());
        mvc.perform(put("/api/v1/repartos/solicitudes/{id}/rechazar", rejectedTransfer)
                        .header("Authorization", bearer(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"El cierre fÃ­sico no coincide; volver a contar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.motivoRechazo").value("El cierre fÃ­sico no coincide; volver a contar"));
        assertEquals(0, balanceBeforeRejection.compareTo(balance(courier)));

        mvc.perform(get("/api/v1/repartos/caja/transferencias").header("Authorization", bearer(courier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].motivoRechazo").isNotEmpty());

        mvc.perform(get("/api/v1/vehiculos").header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/vehiculos").header("Authorization", bearer(courier))
                        .contentType(MediaType.APPLICATION_JSON).content(vehicleJson("NOAUT01", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/vehiculos").header("Authorization", bearer(cashier))
                        .contentType(MediaType.APPLICATION_JSON).content(vehicleJson("NOAUT02", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/vehiculos").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/repartos/dashboard").header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkoutRechazaPagoLocalParaEnvioADomicilio() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        register("cliente.incompatible@techstore.py");
        String customer = login("cliente.incompatible@techstore.py", "Cliente123");
        long address = createAddress(customer);
        long product = createProduct(admin);
        mvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],
                                 "metodoEntrega":"ENVIO_DOMICILIO","metodoPago":"PAGO_EN_LOCAL",
                                 "direccionEntregaId":%d}
                                """.formatted(product, address)))
                .andExpect(status().isConflict());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Cliente","apellido":"Ruta","email":"%s","password":"Cliente123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private JsonNode createUser(String token, String name, String lastName, String email,
                                String password, String role) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"%s","apellido":"%s","email":"%s","password":"%s","rol":"%s"}
                                """.formatted(name, lastName, email, password, role)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private long createAddress(String token) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/direcciones")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreDestinatario":"Cliente Ruta","telefono":"0981123456",
                                 "departamento":"Capital","ciudad":"AsunciÃ³n","barrio":"Centro",
                                 "direccionLinea1":"Palma","numeroCasa":"123",
                                 "referencia":"Frente a la plaza","latitud":-25.2822,
                                 "longitud":-57.6351,"principal":true}
                                """))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createProduct(String token) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/productos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Producto reparto %d","descripcion":"IntegraciÃ³n",
                                 "precio":150000,"iva":10,"stockReal":20,"stockMinimo":1,
                                 "marcaId":null,"modeloId":null,"imagenUrl":null,"activo":true}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long checkout(String token, long product, long address) throws Exception {
        MvcResult quoteResult = mvc.perform(post("/api/v1/envios/cotizar")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direccionEntregaId":%d,
                                 "items":[{"productoId":%d,"cantidad":1}]}
                                """.formatted(address, product)))
                .andExpect(status().isOk()).andReturn();
        JsonNode quotes = json.readTree(quoteResult.getResponse().getContentAsString());
        long tariff = -1;
        for (JsonNode quote : quotes) {
            if (!"RETIRO_TIENDA".equals(quote.get("tipo").asText())) {
                tariff = quote.get("tarifaId").asLong();
                break;
            }
        }
        assertTrue(tariff > 0, "La base de prueba debe ofrecer al menos una tarifa a domicilio");
        MvcResult result = mvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "it-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],
                                 "metodoEntrega":"ENVIO_DOMICILIO",
                                 "metodoPago":"PAGO_EFECTIVO_REPARTIDOR",
                                 "direccionEntregaId":%d,"tarifaEnvioId":%d}
                                """.formatted(product, address, tariff)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long assign(String token, long order, long courier, int orderInRoute) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/repartos/pedidos/{id}/asignar", order)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repartidorId":%d,"ordenEntrega":%d,"observaciones":"Ruta IT"}
                                """.formatted(courier, orderInRoute)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void changeDelivery(String token, long assignment, String state, String result) throws Exception {
        String body = result == null
                ? "{\"estado\":\"%s\"}".formatted(state)
                : "{\"estado\":\"%s\",\"resultadoFinal\":\"%s\"}".formatted(state, result);
        mvc.perform(put("/api/v1/repartos/{id}/estado", assignment)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private long createVehicle(String token, String plate, BigDecimal mileage) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/vehiculos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(plate, mileage)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String vehicleJson(String plate, Number mileage) {
        return """
                {"matricula":"%s","marca":"Honda","modelo":"Wave","anio":2025,
                 "tipo":"MOTO","color":"Azul","kilometrajeActual":%s,
                 "tipoCombustible":"Nafta","estado":"DISPONIBLE","observaciones":"IT"}
                """.formatted(plate, mileage);
    }

    private BigDecimal balance(String token) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/repartos/caja/saldo")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("saldo").decimalValue();
    }

    private long requestTransfer(String token, long cashier) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/repartos/caja/vaciar")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cajeroId\":%d,\"observacion\":\"Cierre IT\"}".formatted(cashier)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private BigDecimal transferAmount(String token, long transfer) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/repartos/caja/transferencias")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode item : json.readTree(result.getResponse().getContentAsString()).get("content")) {
            if (item.get("id").asLong() == transfer) return item.get("monto").decimalValue();
        }
        return BigDecimal.valueOf(-1);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
