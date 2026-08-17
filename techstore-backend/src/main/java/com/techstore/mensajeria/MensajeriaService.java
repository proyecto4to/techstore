package com.techstore.mensajeria;

import com.techstore.comun.BusinessException;
import com.techstore.comun.PageResponse;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.notificaciones.outbox.NotificationOutboxPublisher;
import com.techstore.usuarios.TooManyAttemptsException;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoRepository;
import com.techstore.ventas.TipoPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MensajeriaService {
    private static final Set<String> ROLES_STAFF = Set.of("Admin", "Cajero");
    private static final int MAX_MENSAJES_POR_MINUTO = 30;

    private final ConversacionRepository conversacionRepository;
    private final MensajeRepository mensajeRepository;
    private final UsuarioRepository usuarioRepository;
    private final PedidoRepository pedidoRepository;
    private final ChatRealtimePublisher realtime;
    private final NotificationOutboxPublisher notificationPublisher;

    @Transactional
    public ConversacionCreadaResponse crear(String email, CrearConversacionRequest request) {
        Usuario cliente = usuarioRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        if (esStaff(cliente)) throw new BusinessException("chat.solo.cliente.inicia");
        Pedido pedido = request.pedidoId() == null ? null : pedidoPropio(cliente, request.pedidoId());
        Conversacion conversacion = conversacionRepository
                .findAbierta(cliente.getId(), pedido == null ? null : pedido.getId(), EstadoConversacion.ABIERTA)
                .orElseGet(() -> {
                    Conversacion nueva = new Conversacion();
                    nueva.setCliente(cliente);
                    nueva.setPedido(pedido);
                    nueva.setEstado(EstadoConversacion.ABIERTA);
                    return conversacionRepository.save(nueva);
                });
        MensajeResponse mensaje = enviarInterno(conversacion, cliente,
                new EnviarMensajeRequest(request.clientMessageId(), request.mensaje()));
        return new ConversacionCreadaResponse(respuesta(conversacion, cliente), mensaje);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversacionResponse> listar(String email, Pageable pageable) {
        Usuario usuario = usuario(email);
        var page = esStaff(usuario)
                ? conversacionRepository.findAllBy(pageable)
                : conversacionRepository.findByClienteEmail(email, pageable);
        return PageResponse.from(page.map(c -> respuesta(c, usuario)));
    }

    @Transactional(readOnly = true)
    public ConversacionResponse obtener(Long id, String email) {
        Usuario usuario = usuario(email);
        Conversacion conversacion = exigirAcceso(id, usuario);
        return respuesta(conversacion, usuario);
    }

    @Transactional(readOnly = true)
    public PageResponse<MensajeResponse> historial(Long id, String email, Pageable pageable) {
        Usuario usuario = usuario(email);
        exigirAcceso(id, usuario);
        return PageResponse.from(mensajeRepository
                .findByConversacionIdOrderByEnviadoEnDescIdDesc(id, pageable)
                .map(this::respuesta));
    }

    @Transactional(readOnly = true)
    public UnreadMessageCountResponse contarNoLeidos(String email) {
        Usuario usuario = usuario(email);
        long total = esStaff(usuario)
                ? mensajeRepository.contarNoLeidosStaffGlobal()
                : mensajeRepository.contarNoLeidosClienteGlobal(usuario.getId());
        return new UnreadMessageCountResponse(total);
    }

    @Transactional
    public MensajeResponse enviar(Long id, String email, EnviarMensajeRequest request) {
        Usuario remitente = usuario(email);
        Conversacion conversacion = exigirAcceso(id, remitente);
        return enviarInterno(conversacion, remitente, request);
    }

    @Transactional
    public LecturaResponse marcarLeido(Long id, String email) {
        Usuario lector = usuario(email);
        Conversacion conversacion = exigirAcceso(id, lector);
        LocalDateTime ahora = LocalDateTime.now();
        int actualizados = esStaff(lector)
                ? mensajeRepository.marcarLeidosStaff(id, conversacion.getCliente().getId(), ahora)
                : mensajeRepository.marcarLeidosCliente(id, lector.getId(), ahora);
        LecturaResponse response = new LecturaResponse(id, email, actualizados, ahora);
        realtime.lectura(destinatarios(conversacion), response);
        return response;
    }

    @Transactional
    public ConversacionResponse cerrar(Long id, String email) {
        Usuario usuario = usuario(email);
        Conversacion conversacion = exigirAcceso(id, usuario);
        conversacion.setEstado(EstadoConversacion.CERRADA);
        return respuesta(conversacionRepository.save(conversacion), usuario);
    }

    private MensajeResponse enviarInterno(Conversacion conversacion, Usuario remitente, EnviarMensajeRequest request) {
        if (conversacion.getEstado() != EstadoConversacion.ABIERTA) {
            throw new BusinessException("chat.conversacion.cerrada");
        }
        String clientId = request.clientMessageId().trim();
        String contenido = request.contenido().trim();
        var existente = mensajeRepository.findByRemitenteIdAndClientMessageId(remitente.getId(), clientId);
        if (existente.isPresent()) {
            Mensaje mensaje = existente.get();
            if (!mensaje.getConversacion().getId().equals(conversacion.getId())
                    || !mensaje.getContenido().equals(contenido)) {
                throw new BusinessException("chat.idempotencia.conflicto");
            }
            return respuesta(mensaje);
        }
        if (mensajeRepository.countByRemitenteIdAndEnviadoEnAfter(
                remitente.getId(), LocalDateTime.now().minusMinutes(1)) >= MAX_MENSAJES_POR_MINUTO) {
            throw new TooManyAttemptsException("chat.demasiados.mensajes", 1);
        }

        Mensaje mensaje = new Mensaje();
        mensaje.setConversacion(conversacion);
        mensaje.setRemitente(remitente);
        mensaje.setClientMessageId(clientId);
        mensaje.setTipo(TipoMensaje.TEXTO);
        mensaje.setContenido(contenido);
        mensaje.setEnviadoEn(LocalDateTime.now());
        Mensaje guardado = mensajeRepository.save(mensaje);
        conversacion.setUltimoMensajeEn(guardado.getEnviadoEn());
        conversacionRepository.save(conversacion);
        MensajeResponse response = respuesta(guardado);
        notificationPublisher.mensajeNuevo(guardado, destinatariosNotificacion(conversacion, remitente));
        realtime.mensaje(destinatarios(conversacion), response);
        return response;
    }

    private Pedido pedidoPropio(Usuario cliente, Long id) {
        Pedido pedido = pedidoRepository.findWithDetallesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ventas.pedido.no.encontrado", id));
        if (pedido.getTipo() != TipoPedido.Venta || !pedido.getUsuario().getId().equals(cliente.getId())) {
            throw new AccessDeniedException("No puede asociar una conversación a ese pedido");
        }
        return pedido;
    }

    private Conversacion exigirAcceso(Long id, Usuario usuario) {
        Conversacion conversacion = conversacionRepository.findParaAccesoById(id)
                .orElseThrow(() -> new ResourceNotFoundException("chat.conversacion.no.encontrada", id));
        if (!esStaff(usuario) && !conversacion.getCliente().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No puede acceder a conversaciones de otro usuario");
        }
        return conversacion;
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
    }

    private boolean esStaff(Usuario usuario) {
        return ROLES_STAFF.contains(usuario.getRol().getNombre());
    }

    private List<String> destinatarios(Conversacion conversacion) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        emails.add(conversacion.getCliente().getEmail());
        usuarioRepository.findByRolNombreInAndActivoTrue(ROLES_STAFF).stream()
                .map(Usuario::getEmail).forEach(emails::add);
        return List.copyOf(emails);
    }

    private List<Long> destinatariosNotificacion(Conversacion conversacion, Usuario remitente) {
        if (esStaff(remitente)) {
            return List.of(conversacion.getCliente().getId());
        }
        return usuarioRepository.findByRolNombreInAndActivoTrue(ROLES_STAFF).stream()
                .map(Usuario::getId)
                .toList();
    }

    private ConversacionResponse respuesta(Conversacion c, Usuario solicitante) {
        MensajeResponse ultimo = mensajeRepository.findFirstByConversacionIdOrderByEnviadoEnDescIdDesc(c.getId())
                .map(this::respuesta).orElse(null);
        long noLeidos = esStaff(solicitante)
                ? mensajeRepository.contarNoLeidosStaff(c.getId(), c.getCliente().getId())
                : mensajeRepository.contarNoLeidosCliente(c.getId(), c.getCliente().getId());
        return new ConversacionResponse(c.getId(), c.getEstado().name(), c.getCliente().getId(),
                c.getCliente().getNombre() + " " + c.getCliente().getApellido(), c.getCliente().getEmail(),
                c.getPedido() == null ? null : c.getPedido().getId(),
                c.getPedido() == null ? null : c.getPedido().getNumero(), ultimo, noLeidos,
                c.getUltimoMensajeEn(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private MensajeResponse respuesta(Mensaje m) {
        Usuario r = m.getRemitente();
        return new MensajeResponse(m.getId(), m.getConversacion().getId(), m.getClientMessageId(),
                m.getTipo().name(), m.getContenido(), r.getId(), r.getNombre() + " " + r.getApellido(),
                r.getEmail(), r.getRol().getNombre(), m.getEnviadoEn(), m.getEntregadoEn(), m.getLeidoEn());
    }
}
