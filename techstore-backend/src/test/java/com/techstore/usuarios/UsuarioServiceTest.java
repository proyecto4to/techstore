package com.techstore.usuarios;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.comun.BusinessException;
import com.techstore.empresa.PuntoExpedicion;
import com.techstore.empresa.PuntoExpedicionService;
import com.techstore.notificaciones.CorreoService;
import com.techstore.notificaciones.EmailTemplateService;
import com.techstore.seguridad.AdministradoresGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private CorreoService correoService;

    @Mock
    private EmailTemplateService emailTemplateService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private PuntoExpedicionService puntoExpedicionService;

    @Mock
    private AdministradoresGuard administradores;

    private UsuarioService usuarioService;
    private Rol rolCajero;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(
                usuarioRepository, rolRepository, encoder, correoService, emailTemplateService,
                auditoriaService, puntoExpedicionService, administradores);
        rolCajero = new Rol();
        rolCajero.setId(2L);
        rolCajero.setNombre("Cajero");
    }

    private CrearUsuarioRequest requestCajero() {
        return new CrearUsuarioRequest("Caja", "Uno", "cajero@techstore.py", "cajero1234", "Cajero");
    }

    @Test
    void creaUsuarioConRolYPasswordHasheada() {
        when(usuarioRepository.existsByEmail("cajero@techstore.py")).thenReturn(false);
        when(rolRepository.findByNombre("Cajero")).thenReturn(Optional.of(rolCajero));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(9L);
            return u;
        });

        UsuarioAdminResponse respuesta = usuarioService.crear(requestCajero());

        assertEquals("Cajero", respuesta.rol());
        assertTrue(respuesta.activo());
    }

    @Test
    void emailDuplicadoFalla() {
        when(usuarioRepository.existsByEmail("cajero@techstore.py")).thenReturn(true);
        assertThrows(BusinessException.class, () -> usuarioService.crear(requestCajero()));
    }

    @Test
    void rolInexistenteFalla() {
        when(usuarioRepository.existsByEmail(any())).thenReturn(false);
        when(rolRepository.findByNombre("Gerente")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> usuarioService.crear(
                new CrearUsuarioRequest("A", "B", "x@y.py", "clave12345", "Gerente")));
    }

    @Test
    void noPuedeDesactivarseASiMismo() {
        Usuario admin = new Usuario();
        admin.setId(1L);
        admin.setEmail("admin@techstore.py");
        admin.setRol(rolCajero);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(BusinessException.class,
                () -> usuarioService.cambiarActivo(1L, false, "admin@techstore.py"));
    }

    @Test
    void desactivaAOtroUsuario() {
        Usuario cajero = new Usuario();
        cajero.setId(9L);
        cajero.setEmail("cajero@techstore.py");
        cajero.setRol(rolCajero);
        cajero.setActivo(true);
        when(usuarioRepository.findById(9L)).thenReturn(Optional.of(cajero));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioAdminResponse respuesta = usuarioService.cambiarActivo(9L, false, "admin@techstore.py");

        assertFalse(respuesta.activo());
    }

    private Usuario cajeroGuardado() {
        Usuario cajero = new Usuario();
        cajero.setId(9L);
        cajero.setEmail("cajero@techstore.py");
        cajero.setRol(rolCajero);
        cajero.setActivo(true);
        when(usuarioRepository.findById(9L)).thenReturn(Optional.of(cajero));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        return cajero;
    }

    @Test
    void asignaElPuntoDesdeElQueEmite() {
        cajeroGuardado();
        PuntoExpedicion caja2 = new PuntoExpedicion();
        caja2.setId(2L);
        caja2.setEstablecimiento("001");
        caja2.setPunto("002");
        caja2.setNombre("Caja 2");
        when(puntoExpedicionService.porId(2L)).thenReturn(caja2);

        UsuarioAdminResponse respuesta = usuarioService.asignarPuntoExpedicion(9L, 2L);

        assertEquals(2L, respuesta.puntoExpedicionId());
        assertEquals("001-002 · Caja 2", respuesta.puntoExpedicion());
    }

    @Test
    void sinPuntoVuelveAlPorDefecto() {
        // Nulo no es un dato faltante: es "emite desde el punto por defecto de
        // la empresa", que es como funcionaban todos antes de que hubiera varios.
        Usuario cajero = cajeroGuardado();
        PuntoExpedicion caja2 = new PuntoExpedicion();
        caja2.setId(2L);
        caja2.setEstablecimiento("001");
        caja2.setPunto("002");
        caja2.setNombre("Caja 2");
        cajero.setPuntoExpedicion(caja2);

        UsuarioAdminResponse respuesta = usuarioService.asignarPuntoExpedicion(9L, null);

        assertNull(respuesta.puntoExpedicionId());
        assertNull(respuesta.puntoExpedicion());
    }
}
