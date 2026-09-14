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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los permisos individuales y la administración de Seguridad, aplicados de
 * verdad sobre la API.
 *
 * <p>Todo lo que se comprueba acá se comprueba llamando al endpoint a mano, sin
 * pasar por ninguna pantalla: es la única forma de distinguir una restricción
 * real de un botón escondido.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Seguridad por usuario")
class SeguridadPorUsuarioIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    // ------------------------------------------------------------------
    // Quién entra al módulo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un cajero no toca ningún endpoint de administración de Seguridad")
    void elCajeroNoEntraANingunEndpointDeSeguridad() throws Exception {
        String cajero = nuevoUsuario("seg.cajero.cerrado@techstore.py", "Cajero");

        mvc.perform(get("/api/v1/seguridad/catalogo").header("Authorization", bearer(cajero)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/seguridad/roles/detalle").header("Authorization", bearer(cajero)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/seguridad/usuarios/1/permisos").header("Authorization", bearer(cajero)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/seguridad/usuarios/1/permisos")
                        .header("Authorization", bearer(cajero))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modulo\":\"PRODUCTOS\",\"accion\":\"VER\",\"permitido\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/seguridad/roles")
                        .header("Authorization", bearer(cajero))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Inventado\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin sesión tampoco, aunque se conozca la ruta")
    void sinSesionNoHayModuloDeSeguridad() throws Exception {
        mvc.perform(get("/api/v1/seguridad/catalogo")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cada uno puede preguntar qué puede hacer, aunque no administre Seguridad")
    void cualquieraConsultaSusPropiosPermisos() throws Exception {
        String cajero = nuevoUsuario("seg.cajero.mispermisos@techstore.py", "Cajero");

        MvcResult resultado = mvc.perform(get("/api/v1/auth/mis-permisos")
                        .header("Authorization", bearer(cajero)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode permisos = json.readTree(resultado.getResponse().getContentAsString());
        assertEquals("Cajero", permisos.path("rol").asText());
        assertTrue(contiene(permisos.path("permitidos"), "productos.ver"),
                "El cajero ve el catálogo y eso tiene que estar en sus permisos");
        assertFalse(contiene(permisos.path("permitidos"), "seguridad.ver"),
                "El cajero no administra Seguridad");
        assertTrue(permisos.path("pantallas").isArray(),
                "El cliente necesita saber qué pantallas mostrar");
    }

    // ------------------------------------------------------------------
    // La excepción individual
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Restringirle una acción a una persona le cierra el endpoint, aunque su rol lo permita")
    void laRestriccionIndividualCierraLoQueElRolAbre() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        String email = "seg.restringido@techstore.py";
        String cajero = nuevoUsuario(email, "Cajero");
        long id = idDe(admin, email);

        // El rol Cajero ve el catálogo operativo.
        mvc.perform(get("/api/v1/productos/operacion").param("size", "1")
                        .header("Authorization", bearer(cajero)))
                .andExpect(status().isOk());

        excepcion(admin, id, "PRODUCTOS", "VER", false, "Mostrador sin acceso al catálogo interno");

        // A esta persona, y solo a esta, se le cerró.
        mvc.perform(get("/api/v1/productos/operacion").param("size", "1")
                        .header("Authorization", bearer(cajero)))
                .andExpect(status().isForbidden());

        String otro = nuevoUsuario("seg.otro.cajero@techstore.py", "Cajero");
        mvc.perform(get("/api/v1/productos/operacion").param("size", "1")
                        .header("Authorization", bearer(otro)))
                .andExpect(status().isOk());

        // Quitar la excepción lo devuelve a lo que diga el rol.
        mvc.perform(delete("/api/v1/seguridad/usuarios/{id}/permisos/{modulo}/{accion}",
                        id, "PRODUCTOS", "VER")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/productos/operacion").param("size", "1")
                        .header("Authorization", bearer(cajero)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Concederle una acción no abre lo que el backend niega por código")
    void laConcesionIndividualNoRompeElPiso() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        String email = "seg.concedido@techstore.py";
        String cajero = nuevoUsuario(email, "Cajero");
        long id = idDe(admin, email);

        excepcion(admin, id, "USUARIOS", "VER", true, "Prueba");

        // La anotación del controlador sigue siendo el piso: administrar
        // usuarios es de Admin y ninguna casilla lo cambia.
        mvc.perform(get("/api/v1/usuarios").header("Authorization", bearer(cajero)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Los permisos efectivos dicen de dónde sale cada uno")
    void losPermisosEfectivosTraenSuOrigen() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        String email = "seg.efectivos@techstore.py";
        nuevoUsuario(email, "Cajero");
        long id = idDe(admin, email);

        excepcion(admin, id, "REPORTES", "EDITAR", true, "Arma los informes del mes");
        excepcion(admin, id, "FACTURAS", "CREAR", false, "Todavía en capacitación");

        JsonNode efectivos = json.readTree(mvc.perform(
                        get("/api/v1/seguridad/usuarios/{id}/permisos", id)
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        JsonNode concedido = accion(efectivos, "REPORTES", "EDITAR");
        assertTrue(concedido.path("permitido").asBoolean());
        assertEquals("CONCEDIDO", concedido.path("origen").asText());
        assertEquals("Arma los informes del mes", concedido.path("motivo").asText());

        JsonNode restringido = accion(efectivos, "FACTURAS", "CREAR");
        assertFalse(restringido.path("permitido").asBoolean());
        assertEquals("RESTRINGIDO", restringido.path("origen").asText());

        JsonNode heredado = accion(efectivos, "PRODUCTOS", "VER");
        assertTrue(heredado.path("permitido").asBoolean());
        assertEquals("ROL", heredado.path("origen").asText());
        assertFalse(heredado.path("etiquetaOrigen").asText().isBlank(),
                "La pantalla muestra el origen en castellano, no la constante");
    }

    // ------------------------------------------------------------------
    // Que no se pueda romper el sistema
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Nadie puede quitarse a sí mismo el acceso a Seguridad")
    void nadieSeQuitaLaLlaveEstandoAdentro() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        long propio = idDe(admin, "admin@techstore.py");

        mvc.perform(put("/api/v1/seguridad/usuarios/{id}/permisos", propio)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modulo\":\"SEGURIDAD\",\"accion\":\"EDITAR\",\"permitido\":false}"))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/v1/usuarios/{id}/activo", propio)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isConflict());

        // El sistema sigue administrable.
        mvc.perform(get("/api/v1/seguridad/catalogo").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("No se puede dejar al sistema sin ningún administrador con mando")
    void elUltimoAdministradorNoSeDesactiva() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        long propio = idDe(admin, "admin@techstore.py");

        String email = "seg.admin.temporal@techstore.py";
        String temporal = nuevoUsuario(email, "Admin");
        long idTemporal = idDe(admin, email);

        // Con dos administradores, restringirle Seguridad a uno está permitido.
        excepcion(admin, idTemporal, "SEGURIDAD", "EDITAR", false, "Suplente sin permisos de seguridad");

        // El suplente sigue siendo Admin y puede administrar usuarios, pero ya
        // no cuenta como administrador con mando: desactivar al que queda
        // dejaría el sistema sin nadie que pueda repartir permisos.
        mvc.perform(put("/api/v1/usuarios/{id}/activo", propio)
                        .header("Authorization", bearer(temporal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isConflict());

        // Nada de esto dejó al administrador de siempre afuera.
        mvc.perform(get("/api/v1/seguridad/catalogo").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        // Se deja el entorno como estaba: sin la excepción y sin el suplente.
        mvc.perform(delete("/api/v1/seguridad/usuarios/{id}/permisos/{modulo}/{accion}",
                        idTemporal, "SEGURIDAD", "EDITAR")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/usuarios/{id}/activo", idTemporal)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Al rol Administrador no se le quita el módulo de Seguridad")
    void elRolAdministradorNoPierdeSeguridad() throws Exception {
        String admin = login("admin@techstore.py", "admin123");

        mvc.perform(put("/api/v1/seguridad/roles/Admin/permisos")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modulo\":\"SEGURIDAD\",\"accion\":\"EDITAR\",\"permitido\":false}"))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/v1/seguridad/roles/{rol}", "Admin")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isConflict());

        // La pantalla de seguridad tampoco se le puede esconder al Admin.
        mvc.perform(put("/api/v1/seguridad/roles/Admin/pantallas")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pantalla\":\"seguridad\",\"permitido\":false}"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // Roles y catálogo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un rol nuevo nace sin ningún permiso")
    void elRolNuevoNaceSinPermisos() throws Exception {
        String admin = login("admin@techstore.py", "admin123");

        mvc.perform(post("/api/v1/seguridad/roles")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Consulta\",\"descripcion\":\"Solo mira\"}"))
                .andExpect(status().isCreated());

        JsonNode matriz = json.readTree(mvc.perform(
                        get("/api/v1/seguridad/roles/Consulta/permisos")
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        for (JsonNode modulo : matriz.path("modulos")) {
            for (JsonNode casilla : modulo.path("acciones")) {
                assertFalse(casilla.path("permitido").asBoolean(),
                        "Un rol recién creado no puede venir con permisos puestos");
            }
        }

        // Y no se puede repetir el nombre.
        mvc.perform(post("/api/v1/seguridad/roles")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"consulta\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("El catálogo trae los módulos, las acciones y qué ruta gobierna cada uno")
    void elCatalogoSeArmaSolo() throws Exception {
        String admin = login("admin@techstore.py", "admin123");

        JsonNode catalogo = json.readTree(mvc.perform(get("/api/v1/seguridad/catalogo")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertTrue(catalogo.path("modulos").size() > 10, "Faltan módulos en el catálogo");
        assertEquals(4, catalogo.path("acciones").size(), "Ver, crear, editar y eliminar");
        assertTrue(catalogo.path("rutas").size() > 10,
                "Quien configura tiene que poder ver qué ruta gobierna cada módulo");
        for (JsonNode modulo : catalogo.path("modulos")) {
            assertFalse(modulo.path("etiqueta").asText().isBlank(),
                    "Un módulo sin nombre para mostrar obliga a leer la constante");
        }
    }

    // ------------------------------------------------------------------
    // Auditoría
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El intento rechazado queda auditado, con su plataforma")
    void elRechazoQuedaAuditado() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        String email = "seg.auditado@techstore.py";
        String cajero = nuevoUsuario(email, "Cajero");
        long id = idDe(admin, email);

        excepcion(admin, id, "COMPRAS", "VER", false, "No compra");

        mvc.perform(get("/api/v1/compras").param("size", "1")
                        .header("Authorization", bearer(cajero))
                        .header("X-Plataforma", "Movil"))
                .andExpect(status().isForbidden());

        JsonNode auditoria = json.readTree(mvc.perform(get("/api/v1/auditoria")
                        .param("entidad", "Acceso")
                        .param("resultado", "Rechazado")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        JsonNode registro = null;
        for (JsonNode fila : auditoria.path("content")) {
            if (email.equals(fila.path("usuarioEmail").asText())) {
                registro = fila;
                break;
            }
        }
        assertNotNull(registro, "El intento rechazado tiene que dejar rastro");
        assertEquals("Rechazado", registro.path("resultado").asText());
        assertEquals("Movil", registro.path("plataforma").asText());
        assertEquals("Cajero", registro.path("usuarioRol").asText());
        assertTrue(registro.path("detalle").asText().contains("/api/v1/compras"),
                "El rastro tiene que decir qué se intentó");
    }

    // ------------------------------------------------------------------

    private void excepcion(String admin, long usuarioId, String modulo, String accion,
                           boolean permitido, String motivo) throws Exception {
        mvc.perform(put("/api/v1/seguridad/usuarios/{id}/permisos", usuarioId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modulo\":\"" + modulo + "\",\"accion\":\"" + accion
                                + "\",\"permitido\":" + permitido + ",\"motivo\":\"" + motivo + "\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode accion(JsonNode efectivos, String modulo, String accion) {
        for (JsonNode fila : efectivos.path("modulos")) {
            if (modulo.equals(fila.path("modulo").asText())) {
                for (JsonNode casilla : fila.path("acciones")) {
                    if (accion.equals(casilla.path("accion").asText())) {
                        return casilla;
                    }
                }
            }
        }
        throw new AssertionError("No vino " + modulo + "." + accion + " en los permisos efectivos");
    }

    private static boolean contiene(JsonNode lista, String valor) {
        for (JsonNode elemento : lista) {
            if (valor.equals(elemento.asText())) {
                return true;
            }
        }
        return false;
    }

    private long idDe(String admin, String email) throws Exception {
        MvcResult resultado = mvc.perform(get("/api/v1/usuarios")
                        .param("q", email)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode contenido = json.readTree(resultado.getResponse().getContentAsString()).path("content");
        assertTrue(contenido.size() > 0, "No se encontró " + email);
        return contenido.get(0).path("id").asLong();
    }

    private String nuevoUsuario(String email, String rol) throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Prueba\",\"apellido\":\"Seguridad\",\"email\":\"" + email
                                + "\",\"password\":\"Prueba123\",\"rol\":\"" + rol + "\"}"))
                .andExpect(status().is2xxSuccessful());
        return login(email, "Prueba123");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String login(String email, String password) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(resultado.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
