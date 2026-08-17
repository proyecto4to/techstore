package com.techstore.mensajeria;

import com.techstore.notificaciones.outbox.NotificationOutboxPublisher;
import com.techstore.usuarios.Rol;
import com.techstore.usuarios.TooManyAttemptsException;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import com.techstore.ventas.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensajeriaServiceTest {
    @Mock private ConversacionRepository conversacionRepository;
    @Mock private MensajeRepository mensajeRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private ChatRealtimePublisher realtime;
    @Mock private NotificationOutboxPublisher notificationPublisher;

    private MensajeriaService service;

    @BeforeEach
    void setUp() {
        service = new MensajeriaService(conversacionRepository, mensajeRepository,
                usuarioRepository, pedidoRepository, realtime, notificationPublisher);
    }

    @Test
    void reintentoConMismoClientMessageIdDevuelveElMensajeSinDuplicarlo() {
        Usuario cliente = usuario(3L, "cliente@techstore.py", "Cliente");
        Conversacion conversacion = conversacion(9L, cliente);
        Mensaje existente = mensaje(11L, conversacion, cliente, "mobile-1", "Necesito ayuda");
        when(usuarioRepository.findByEmail(cliente.getEmail())).thenReturn(Optional.of(cliente));
        when(conversacionRepository.findParaAccesoById(9L)).thenReturn(Optional.of(conversacion));
        when(mensajeRepository.findByRemitenteIdAndClientMessageId(3L, "mobile-1"))
                .thenReturn(Optional.of(existente));

        MensajeResponse response = service.enviar(9L, cliente.getEmail(),
                new EnviarMensajeRequest("mobile-1", "Necesito ayuda"));

        assertEquals(11L, response.id());
        verify(mensajeRepository, never()).save(any());
        verify(realtime, never()).mensaje(any(), any());
    }

    @Test
    void rechazaAccesoAConversacionDeOtroCliente() {
        Usuario propietario = usuario(3L, "propietario@techstore.py", "Cliente");
        Usuario intruso = usuario(4L, "intruso@techstore.py", "Cliente");
        Conversacion conversacion = conversacion(9L, propietario);
        when(usuarioRepository.findByEmail(intruso.getEmail())).thenReturn(Optional.of(intruso));
        when(conversacionRepository.findParaAccesoById(9L)).thenReturn(Optional.of(conversacion));

        assertThrows(AccessDeniedException.class, () -> service.historial(
                9L, intruso.getEmail(), org.springframework.data.domain.PageRequest.of(0, 20)));
        verify(mensajeRepository, never()).findByConversacionIdOrderByEnviadoEnDescIdDesc(any(), any());
    }

    @Test
    void staffMarcaLeidosSoloLosMensajesDelClienteAutorizado() {
        Usuario cliente = usuario(3L, "cliente@techstore.py", "Cliente");
        Usuario admin = usuario(2L, "admin@techstore.py", "Admin");
        Conversacion conversacion = conversacion(9L, cliente);
        when(usuarioRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByRolNombreInAndActivoTrue(any())).thenReturn(List.of(admin));
        when(conversacionRepository.findParaAccesoById(9L)).thenReturn(Optional.of(conversacion));
        when(mensajeRepository.marcarLeidosStaff(eq(9L), eq(3L), any(LocalDateTime.class))).thenReturn(2);

        LecturaResponse response = service.marcarLeido(9L, admin.getEmail());

        assertEquals(2, response.mensajes());
        verify(mensajeRepository).marcarLeidosStaff(eq(9L), eq(3L), any(LocalDateTime.class));
        verify(realtime).lectura(eq(List.of(cliente.getEmail(), admin.getEmail())), eq(response));
    }

    @Test
    void limitaMensajesPersistidosPorRemitenteYMinuto() {
        Usuario cliente = usuario(3L, "cliente@techstore.py", "Cliente");
        Conversacion conversacion = conversacion(9L, cliente);
        when(usuarioRepository.findByEmail(cliente.getEmail())).thenReturn(Optional.of(cliente));
        when(conversacionRepository.findParaAccesoById(9L)).thenReturn(Optional.of(conversacion));
        when(mensajeRepository.findByRemitenteIdAndClientMessageId(3L, "mobile-31"))
                .thenReturn(Optional.empty());
        when(mensajeRepository.countByRemitenteIdAndEnviadoEnAfter(eq(3L), any(LocalDateTime.class)))
                .thenReturn(30L);

        assertThrows(TooManyAttemptsException.class, () -> service.enviar(9L, cliente.getEmail(),
                new EnviarMensajeRequest("mobile-31", "Un mensaje de más")));
        verify(mensajeRepository, never()).save(any());
    }

    @Test
    void cuentaSoloLosMensajesNoLeidosDelClienteAutenticado() {
        Usuario cliente = usuario(3L, "cliente@techstore.py", "Cliente");
        when(usuarioRepository.findByEmail(cliente.getEmail())).thenReturn(Optional.of(cliente));
        when(mensajeRepository.contarNoLeidosClienteGlobal(cliente.getId())).thenReturn(7L);

        UnreadMessageCountResponse response = service.contarNoLeidos(cliente.getEmail());

        assertEquals(7L, response.noLeidos());
        verify(mensajeRepository).contarNoLeidosClienteGlobal(cliente.getId());
        verify(mensajeRepository, never()).contarNoLeidosStaffGlobal();
    }

    private Usuario usuario(Long id, String email, String rolNombre) {
        Rol rol = new Rol();
        rol.setId(id);
        rol.setNombre(rolNombre);
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setTenantId(1L);
        usuario.setEmail(email);
        usuario.setNombre(rolNombre);
        usuario.setApellido("TechStore");
        usuario.setRol(rol);
        usuario.setActivo(true);
        return usuario;
    }

    private Conversacion conversacion(Long id, Usuario cliente) {
        Conversacion conversacion = new Conversacion();
        conversacion.setId(id);
        conversacion.setTenantId(1L);
        conversacion.setCliente(cliente);
        conversacion.setEstado(EstadoConversacion.ABIERTA);
        conversacion.setCreatedAt(LocalDateTime.now());
        conversacion.setUpdatedAt(LocalDateTime.now());
        return conversacion;
    }

    private Mensaje mensaje(Long id, Conversacion conversacion, Usuario remitente,
                            String clientMessageId, String contenido) {
        Mensaje mensaje = new Mensaje();
        mensaje.setId(id);
        mensaje.setTenantId(1L);
        mensaje.setConversacion(conversacion);
        mensaje.setRemitente(remitente);
        mensaje.setClientMessageId(clientMessageId);
        mensaje.setTipo(TipoMensaje.TEXTO);
        mensaje.setContenido(contenido);
        mensaje.setEnviadoEn(LocalDateTime.now());
        return mensaje;
    }
}
