package com.techstore.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Aplica los permisos configurados sobre cada pedido a la API.
 *
 * <p>Existe porque ocultar un botón no restringe nada: mientras el endpoint
 * responda, cualquiera con la sesión lo llama a mano. Acá la regla se aplica
 * antes de llegar al controlador.
 *
 * <p>Es una capa <b>encima</b> de las anotaciones, no en lugar de ellas. Un
 * pedido tiene que pasar las dos: primero el {@code @PreAuthorize} del método,
 * después el permiso configurado. Por eso destildar una casilla cierra, pero
 * tildarla no abre nada que el código niegue.
 *
 * <p>Lo que no está mapeado pasa de largo y queda gobernado por su anotación de
 * siempre. Se prefirió eso a negar por omisión: un endpoint nuevo sin mapear
 * seguiría funcionando como hoy en vez de romperse en silencio.
 */
@Component
@RequiredArgsConstructor
public class PermisoInterceptor implements HandlerInterceptor {

    /**
     * De qué módulo es cada ruta.
     *
     * <p>El orden importa: se toma la primera que calce, así que las rutas más
     * específicas van antes que las que las contienen.
     */
    private static final Map<String, Modulo> RUTAS = rutas();

    private static Map<String, Modulo> rutas() {
        Map<String, Modulo> mapa = new LinkedHashMap<>();
        mapa.put("/api/v1/seguridad", Modulo.SEGURIDAD);
        mapa.put("/api/v1/productos", Modulo.PRODUCTOS);
        mapa.put("/api/v1/marcas", Modulo.PRODUCTOS);
        mapa.put("/api/v1/modelos", Modulo.PRODUCTOS);
        mapa.put("/api/v1/pedidos", Modulo.PEDIDOS);
        mapa.put("/api/v1/carrito", Modulo.PEDIDOS);
        mapa.put("/api/v1/compras", Modulo.COMPRAS);
        mapa.put("/api/v1/proveedores", Modulo.PROVEEDORES);
        mapa.put("/api/v1/caja", Modulo.CAJA);
        mapa.put("/api/v1/facturas", Modulo.FACTURAS);
        mapa.put("/api/v1/notas-credito", Modulo.FACTURAS);
        mapa.put("/api/v1/usuarios", Modulo.USUARIOS);
        mapa.put("/api/v1/empresa", Modulo.EMPRESA);
        mapa.put("/api/v1/envios", Modulo.ENVIOS);
        mapa.put("/api/v1/repartos", Modulo.REPARTOS);
        mapa.put("/api/v1/vehiculos", Modulo.VEHICULOS);
        mapa.put("/api/v1/auditoria", Modulo.AUDITORIA);
        mapa.put("/api/v1/contabilidad", Modulo.CONTABILIDAD);
        mapa.put("/api/v1/correos", Modulo.CONFIGURACION);
        mapa.put("/api/v1/conversaciones", Modulo.CONVERSACIONES);
        mapa.put("/api/v1/reportes", Modulo.REPORTES);
        return mapa;
    }

    /**
     * POST que en realidad consultan.
     *
     * <p>Deducir la accion del verbo funciona casi siempre, pero no cuando se
     * usa POST para preguntar algo que no entra en una query string: cotizar un
     * envio, validar el carrito o comparar proveedores no crean nada. Tratarlos
     * como altas obligaria a darle "crear envios" a un cliente solo para que
     * pueda terminar su compra, que es exactamente el permiso de mas que esta
     * capa deberia evitar.
     */
    private static final Set<String> CONSULTAS_CON_CUERPO = Set.of(
            "/api/v1/envios/cotizar",
            "/api/v1/carrito/validar",
            "/api/v1/compras/comparar");

    private final PermisoService permisos;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        String prefijo = prefijoDe(uri);
        if (prefijo == null) {
            return true;
        }
        Modulo modulo = RUTAS.get(prefijo);
        String rol = rolActual();
        if (rol == null) {
            // Sin sesión no decide esta capa: de eso se ocupa Spring Security.
            return true;
        }
        AccionPermiso accion = accionDe(uri, prefijo, request.getMethod());
        if (permisos.puede(rol, modulo, accion)) {
            return true;
        }
        throw new AccessDeniedException(
                "El rol " + rol + " no tiene permiso de " + accion.etiqueta().toLowerCase()
                        + " en " + modulo.etiqueta());
    }

    private String prefijoDe(String uri) {
        if (uri == null) {
            return null;
        }
        for (String prefijo : RUTAS.keySet()) {
            if (uri.startsWith(prefijo)) {
                return prefijo;
            }
        }
        return null;
    }

    /**
     * Que accion representa este pedido sobre el modulo.
     *
     * <p>El verbo solo no alcanza. Escribir sobre algo colgado de un elemento
     * concreto —cancelar el pedido 73, quitarle un comprobante, cambiarle la
     * foto a un producto— es EDITAR ese elemento, no crear ni borrar elementos
     * del modulo. Sin esta distincion, un cliente necesitaria permiso de
     * "eliminar pedidos" para sacar una foto que el mismo adjunto, y de "crear"
     * para cancelar el suyo: permisos mucho mas anchos que la operacion real.
     *
     * <p>La forma de la ruta lo dice: {@code /modulo} es la coleccion,
     * {@code /modulo/{id}} es el elemento, y cualquier cosa despues del id es
     * algo suyo.
     */
    private AccionPermiso accionDe(String uri, String prefijo, String metodo) {
        if (CONSULTAS_CON_CUERPO.contains(uri)) {
            return AccionPermiso.VER;
        }
        AccionPermiso porVerbo = AccionPermiso.deMetodo(metodo);
        if (porVerbo == AccionPermiso.VER) {
            return porVerbo;
        }
        return esSubRecurso(uri, prefijo) ? AccionPermiso.EDITAR : porVerbo;
    }

    /** Si despues del identificador del elemento todavia queda camino. */
    private static boolean esSubRecurso(String uri, String prefijo) {
        String resto = uri.substring(prefijo.length());
        if (resto.startsWith("/")) {
            resto = resto.substring(1);
        }
        if (resto.isBlank()) {
            return false;
        }
        return resto.contains("/");
    }

    private String rolActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }
}
