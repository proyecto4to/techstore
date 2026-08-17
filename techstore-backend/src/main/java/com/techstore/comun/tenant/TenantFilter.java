package com.techstore.comun.tenant;

import com.techstore.usuarios.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Decide de qué empresa es cada petición y lo deja en {@link TenantContext}.
 *
 * <p>Corre <b>antes</b> del filtro de autenticación para que incluso la consulta
 * que carga al usuario se haga dentro de su empresa. El tenant sale del JWT
 * firmado, nunca de una cabecera libre que el cliente pueda modificar.
 *
 * <p>Para las peticiones anónimas —el catálogo público, el login— se usa el
 * tenant por defecto. En refresh/logout se admite además un refresh JWT firmado
 * en Authorization únicamente para resolver el tenant; el filtro de seguridad
 * sigue sin autenticarlo como access token. Cuando haya más de una empresa con tienda pública, acá va
 * a resolverse por subdominio; hoy sería adivinar cómo, así que se deja el
 * comportamiento actual.
 */
@Component
@Order(TenantFilter.ORDEN)
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    /** Antes de Spring Security, para que la autenticación ya tenga tenant. */
    public static final int ORDEN = Integer.MIN_VALUE + 100;

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            TenantContext.fijar(resolver(request));
            chain.doFilter(request, response);
        } finally {
            // Imprescindible: los hilos se reutilizan entre peticiones, y un
            // tenant que quede pegado le mostraría a la siguiente persona los
            // datos de otra empresa.
            TenantContext.limpiar();
        }
    }

    private Long resolver(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return TenantContext.TENANT_POR_DEFECTO;
        }
        try {
            Claims claims = jwtService.parseToken(authorization.substring(BEARER_PREFIX.length()));
            if (!jwtService.isAccessToken(claims) && !jwtService.isRefreshToken(claims)) {
                return TenantContext.TENANT_POR_DEFECTO;
            }
            Object valor = claims.get(JwtService.CLAIM_TENANT);
            if (valor instanceof Number numero && numero.longValue() > 0) {
                return numero.longValue();
            }
            if (valor instanceof String texto) {
                long tenant = Long.parseLong(texto);
                return tenant > 0 ? tenant : TenantContext.TENANT_POR_DEFECTO;
            }
        } catch (JwtException | NumberFormatException ignored) {
            // El filtro de autenticación rechazará el token. Acá solo evitamos
            // que un token inválido pueda elegir otra empresa.
        }
        return TenantContext.TENANT_POR_DEFECTO;
    }
}
