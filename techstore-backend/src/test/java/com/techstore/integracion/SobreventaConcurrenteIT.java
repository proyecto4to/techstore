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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dos ventas a la vez de la última unidad.
 *
 * <p>El servicio bloquea la fila del producto y vuelve a mirar el stock dentro
 * de la transacción, en lugar de confiar en lo que vio la pantalla. Eso es lo
 * que se comprueba acá, y con dos hilos de verdad: revisando el código, la
 * diferencia entre validar antes o dentro de la transacción no se ve, y el
 * síntoma —dos clientes pagando el mismo aparato, o un stock en menos uno—
 * aparece recién con dos personas comprando al mismo tiempo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Sobreventa con dos ventas simultáneas")
class SobreventaConcurrenteIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        BaseDatosDePrueba.registrar(registro);
    }

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("de la última unidad se vende una sola, y el stock no queda negativo")
    void dosVentasSimultaneasDeLaUltimaUnidad() throws Exception {
        String jwt = token();
        long productoId = producto(jwt, 1);

        List<Respuesta> estados = enParalelo(2, () -> vender(jwt, productoId));

        long vendidas = estados.stream().filter(Respuesta::vendida).count();
        assertEquals(1, vendidas,
                "Había una sola unidad y se cobraron " + vendidas + " ventas. Estados: " + estados);
        assertTrue(estados.stream().anyMatch(Respuesta::rechazoDeNegocio),
                "La venta que sobraba tenía que rebotar con un error de negocio, no de servidor. "
                        + "Estados: " + estados);
        assertEquals(0, stock(jwt, productoId), "El stock quedó en un número imposible");
    }

    @Test
    @DisplayName("cuatro compradores por tres unidades: se venden tres")
    void masCompradoresQueUnidades() throws Exception {
        String jwt = token();
        long productoId = producto(jwt, 3);

        List<Respuesta> estados = enParalelo(4, () -> vender(jwt, productoId));

        assertEquals(3, estados.stream().filter(Respuesta::vendida).count(),
                "Se vendieron más unidades de las que había. Estados: " + estados);
        assertEquals(0, stock(jwt, productoId));
    }

    /**
     * Corre la misma operación en varios hilos, largados todos juntos.
     *
     * <p>La barrera es lo que hace al caso: sin ella los hilos arrancan
     * escalonados, el primero termina antes de que el segundo mire el stock y
     * la prueba pasa aunque no haya ningún candado.
     */
    private List<Respuesta> enParalelo(int hilos, Callable<Respuesta> operacion) throws Exception {
        CyclicBarrier largada = new CyclicBarrier(hilos);
        try (ExecutorService pool = Executors.newFixedThreadPool(hilos)) {
            List<Future<Respuesta>> futuros = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return operacion.call();
                }));
            }
            List<Respuesta> respuestas = new ArrayList<>();
            for (Future<Respuesta> futuro : futuros) {
                respuestas.add(futuro.get());
            }
            return respuestas;
        }
    }

    private Respuesta vender(String jwt, long productoId) throws Exception {
        var respuesta = mvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],
                                 "lineas":[{"tipoPago":"Efectivo","monto":100000.00,
                                            "numeroComprobante":null}],
                                 "receptor":null,"notas":"sobreventa IT"}"""
                                .formatted(productoId)))
                .andReturn()
                .getResponse();
        return new Respuesta(respuesta.getStatus(), respuesta.getContentAsString());
    }

    /**
     * El estado y el cuerpo de cada venta.
     *
     * <p>El cuerpo va en el mensaje del fallo a propósito: un «409» pelado no
     * dice si rebotó por falta de stock —que es lo que se está probando— o por
     * cualquier otra regla del negocio.
     */
    private record Respuesta(int estado, String cuerpo) {
        boolean vendida() {
            return estado == 201;
        }

        boolean rechazoDeNegocio() {
            return estado >= 400 && estado < 500;
        }

        @Override
        public String toString() {
            return estado + (vendida() ? "" : " " + cuerpo);
        }
    }

    private long producto(String jwt, int stock) throws Exception {
        MvcResult creado = mvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Última unidad %d","descripcion":"sobreventa",
                                 "precio":100000.00,"iva":10,"stockReal":%d,"stockMinimo":0,
                                 "marcaId":null,"modeloId":null,"imagenUrl":null,"activo":true}"""
                                .formatted(System.nanoTime(), stock)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(creado.getResponse().getContentAsString()).get("id").asLong();
    }

    private int stock(String jwt, long productoId) throws Exception {
        MvcResult res = mvc.perform(get("/api/v1/productos/admin/" + productoId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode producto = json.readTree(res.getResponse().getContentAsString());
        return producto.get("stockReal").asInt();
    }

    private String token() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@techstore.py","password":"admin123"}"""))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
