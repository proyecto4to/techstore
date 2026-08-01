package com.techstore.tenant;

import com.techstore.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Decide de qué empresa es cada petición y lo deja en {@link TenantContext}.
 *
 * <p>Corre <b>después</b> del filtro de JWT: la fuente de verdad es a qué empresa
 * pertenece el usuario autenticado, no un dato que venga en la petición. Si el
 * tenant llegara en una cabecera que el cliente controla, cualquiera podría
 * pedir los datos de otra empresa cambiando un número.
 *
 * <p>Para las peticiones anónimas —el catálogo público, el login— se usa el
 * tenant por defecto. Cuando haya más de una empresa con tienda pública, acá va
 * a resolverse por subdominio; hoy sería adivinar cómo, así que se deja el
 * comportamiento actual.
 */
@Component
@Order(TenantFilter.ORDEN)
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    /** Después del filtro de JWT, que corre en el orden por defecto de Spring Security. */
    public static final int ORDEN = Integer.MIN_VALUE + 100;

    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            TenantContext.fijar(resolver());
            chain.doFilter(request, response);
        } finally {
            // Imprescindible: los hilos se reutilizan entre peticiones, y un
            // tenant que quede pegado le mostraría a la siguiente persona los
            // datos de otra empresa.
            TenantContext.limpiar();
        }
    }

    private Long resolver() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return TenantContext.TENANT_POR_DEFECTO;
        }
        return usuarioRepository.findByEmail(auth.getName())
                .map(u -> u.getTenantId() == null ? TenantContext.TENANT_POR_DEFECTO : u.getTenantId())
                .orElse(TenantContext.TENANT_POR_DEFECTO);
    }
}
