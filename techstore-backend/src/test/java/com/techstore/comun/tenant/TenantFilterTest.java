package com.techstore.comun.tenant;

import com.techstore.usuarios.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final TenantFilter filter = new TenantFilter(jwtService);

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
    }

    @Test
    void unaPeticionLocalSinTokenUsaLaEmpresaPorDefectoYLimpiaElHilo() throws Exception {
        AtomicReference<Long> durante = new AtomicReference<>();

        ejecutar(new MockHttpServletRequest(),
                (request, response) -> durante.set(TenantContext.actual()));

        assertEquals(TenantContext.TENANT_POR_DEFECTO, durante.get());
        assertFalse(TenantContext.hayTenant());
    }

    @Test
    void elTenantSaleDelTokenFirmadoAntesDeContinuarLaPeticion() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtService.parseToken("firmado")).thenReturn(claims);
        when(jwtService.isAccessToken(claims)).thenReturn(true);
        when(claims.get(JwtService.CLAIM_TENANT)).thenReturn(7L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer firmado");
        AtomicReference<Long> durante = new AtomicReference<>();

        ejecutar(request, (req, response) -> durante.set(TenantContext.actual()));

        assertEquals(7L, durante.get());
        assertFalse(TenantContext.hayTenant());
    }

    @Test
    void unRefreshFirmadoPuedeResolverTenantSinAutenticarLaPeticion() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtService.parseToken("refresh-firmado")).thenReturn(claims);
        when(jwtService.isAccessToken(claims)).thenReturn(false);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);
        when(claims.get(JwtService.CLAIM_TENANT)).thenReturn(9L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer refresh-firmado");
        AtomicReference<Long> durante = new AtomicReference<>();

        ejecutar(request, (req, response) -> durante.set(TenantContext.actual()));

        assertEquals(9L, durante.get());
        assertFalse(TenantContext.hayTenant());
    }

    @Test
    void unTokenInvalidoNoPuedeElegirEmpresa() throws Exception {
        when(jwtService.parseToken("alterado")).thenThrow(new MalformedJwtException("inválido"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer alterado");
        AtomicReference<Long> durante = new AtomicReference<>();

        ejecutar(request, (req, response) -> durante.set(TenantContext.actual()));

        assertEquals(TenantContext.TENANT_POR_DEFECTO, durante.get());
    }

    private void ejecutar(MockHttpServletRequest request, FilterChain chain) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }
}
