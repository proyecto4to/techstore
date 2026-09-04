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
        Modulo modulo = moduloDe(request.getRequestURI());
        if (modulo == null) {
            return true;
        }
        String rol = rolActual();
        if (rol == null) {
            // Sin sesión no decide esta capa: de eso se ocupa Spring Security.
            return true;
        }
        AccionPermiso accion = CONSULTAS_CON_CUERPO.contains(request.getRequestURI())
                ? AccionPermiso.VER
                : AccionPermiso.deMetodo(request.getMethod());
        if (permisos.puede(rol, modulo, accion)) {
            return true;
        }
        throw new AccessDeniedException(
                "El rol " + rol + " no tiene permiso de " + accion.etiqueta().toLowerCase()
                        + " en " + modulo.etiqueta());
    }

    private Modulo moduloDe(String uri) {
        if (uri == null) {
            return null;
        }
        for (Map.Entry<String, Modulo> entrada : RUTAS.entrySet()) {
            if (uri.startsWith(entrada.getKey())) {
                return entrada.getValue();
            }
        }
        return null;
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
