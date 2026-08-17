package com.techstore.usuarios;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {

    private static final String SECRET = "clave-de-prueba-suficientemente-larga-para-hmac-sha!!";

    @Mock
    private SesionRefreshRepository repository;

    private final JwtService jwtService = new JwtService(SECRET, 900_000, 604_800_000);
    private RefreshSessionService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new RefreshSessionService(repository, jwtService);
        when(repository.save(any(SesionRefresh.class))).thenAnswer(inv -> inv.getArgument(0));

        Rol rol = new Rol();
        rol.setId(3L);
        rol.setNombre("Cliente");

        usuario = new Usuario();
        usuario.setId(25L);
        usuario.setTenantId(7L);
        usuario.setEmail("cliente@techstore.py");
        usuario.setNombre("Cliente");
        usuario.setApellido("Prueba");
        usuario.setRol(rol);
        usuario.setActivo(true);
    }

    @Test
    void crearGuardaSoloHashYJti() {
        String token = service.crear(usuario);
        Claims claims = jwtService.parseToken(token);

        ArgumentCaptor<SesionRefresh> captor = ArgumentCaptor.forClass(SesionRefresh.class);
        verify(repository).save(captor.capture());
        SesionRefresh guardada = captor.getValue();

        assertEquals(UUID.fromString(claims.getId()), guardada.getTokenId());
        assertEquals(7L, guardada.getTenantId());
        assertEquals(64, guardada.getTokenHash().length());
        assertNotEquals(token, guardada.getTokenHash());
        assertTrue(guardada.getExpiraEn().isAfter(guardada.getCreadoEn()));
    }

    @Test
    void rotarRevocaLaAnteriorYEmiteOtra() {
        String token = service.crear(usuario);
        ArgumentCaptor<SesionRefresh> inicial = ArgumentCaptor.forClass(SesionRefresh.class);
        verify(repository).save(inicial.capture());
        SesionRefresh anterior = inicial.getValue();
        clearInvocations(repository);
        when(repository.buscarParaActualizar(anterior.getTokenId())).thenReturn(Optional.of(anterior));

        RefreshSessionService.Rotacion rotacion = service.rotar(token);

        assertEquals(usuario, rotacion.usuario());
        assertNotEquals(token, rotacion.refreshToken());
        assertNotNull(anterior.getRevocadoEn());
        assertNotNull(anterior.getReemplazadoPor());
        verify(repository, times(2)).save(any(SesionRefresh.class));
    }

    @Test
    void reutilizarTokenRotadoRevocaLasDemasSesiones() {
        String token = service.crear(usuario);
        ArgumentCaptor<SesionRefresh> inicial = ArgumentCaptor.forClass(SesionRefresh.class);
        verify(repository).save(inicial.capture());
        SesionRefresh reutilizada = inicial.getValue();
        reutilizada.setRevocadoEn(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));

        SesionRefresh otra = new SesionRefresh();
        otra.setUsuario(usuario);
        when(repository.buscarParaActualizar(reutilizada.getTokenId())).thenReturn(Optional.of(reutilizada));
        when(repository.findAllByUsuarioIdAndRevocadoEnIsNull(usuario.getId())).thenReturn(List.of(otra));

        assertThrows(BadCredentialsException.class, () -> service.rotar(token));

        assertNotNull(otra.getRevocadoEn());
        verify(repository).saveAll(List.of(otra));
    }

    @Test
    void logoutEsIdempotenteParaSesionYaRevocada() {
        String token = service.crear(usuario);
        ArgumentCaptor<SesionRefresh> inicial = ArgumentCaptor.forClass(SesionRefresh.class);
        verify(repository).save(inicial.capture());
        SesionRefresh sesion = inicial.getValue();
        sesion.setRevocadoEn(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        clearInvocations(repository);
        when(repository.buscarParaActualizar(sesion.getTokenId())).thenReturn(Optional.of(sesion));

        service.revocar(token);

        verify(repository).buscarParaActualizar(sesion.getTokenId());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
