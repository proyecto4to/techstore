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
import java.util.stream.Collectors;

/**
 * La mensajería, con dos formas de conversación que conviven.
 *
 * <p><b>Soporte</b> es el cliente con la tienda: él la ve como «Atención al
 * Cliente» y la atiende cualquiera del mostrador. <b>Privada</b> es entre dos
 * personas y no la lee nadie más, ni siquiera administración.
 *
 * <p>Quién puede escribirle a quién se decide acá y no en la pantalla: un cajero
 * que pida por API la lista de clientes recibe la lista vacía, y si manda el id
 * de un cliente como destinatario recibe un rechazo.
 */
@Service
@RequiredArgsConstructor
public class MensajeriaService {
    /** Quiénes atienden el soporte del cliente. */
    private static final Set<String> ROLES_MOSTRADOR = Set.of("Admin", "Cajero");
    private static final String ADMIN = "Admin";
    private static final Set<String> TODOS_LOS_ROLES =
            Set.of("Admin", "Cajero", "Repartidor", "Cliente");
    private static final int MAX_MENSAJES_POR_MINUTO = 30;

    private final ConversacionRepository conversacionRepository;
    private final MensajeRepository mensajeRepository;
    private final UsuarioRepository usuarioRepository;
    private final PedidoRepository pedidoRepository;
    private final ChatRealtimePublisher realtime;
    private final NotificationOutboxPublisher notificationPublisher;

    // ------------------------------------------------------------------
    // Con quién se puede hablar
    // ------------------------------------------------------------------

    /**
     * A quiénes puede escribirles el usuario, ya filtrado por su rol.
     *
     * <p>El filtro por rol que llega de la pantalla solo puede <i>achicar</i> lo
     * que el rol de quien pregunta ya permite. Un cajero que pida
     * {@code rol=Cliente} recibe la lista vacía, no la de clientes.
     */
    @Transactional(readOnly = true)
    public List<DestinatarioResponse> destinatarios(String email, String texto, String rol) {
        Usuario usuario = usuario(email);
        Set<String> permitidos = rolesQuePuedeContactar(usuario);
        if (permitidos.isEmpty()) {
            throw new BusinessException("chat.sin.destinatarios");
        }
        Set<String> buscados = rol == null || rol.isBlank()
                ? permitidos
                : permitidos.stream()
                        .filter(candidato -> candidato.equalsIgnoreCase(rol))
                        .collect(Collectors.toSet());
        if (buscados.isEmpty()) {
            return List.of();
        }
        // El patron va armado: «%» trae todo, y asi el parametro nunca es nulo.
        String patron = texto == null || texto.isBlank()
                ? "%"
                : "%" + texto.trim().toLowerCase() + "%";
        return usuarioRepository.buscarParaConversar(buscados, patron, usuario.getId()).stream()
                .map(candidato -> DestinatarioResponse.from(candidato,
                        conversacionRepository.findPrivadaEntre(usuario.getId(), candidato.getId())
                                .map(Conversacion::getId).orElse(null)))
                .toList();
    }

    /**
     * Los roles que este usuario puede contactar.
     *
     * <p>Administración habla con cualquiera. Cajero y repartidor solo con
     * administración: no pueden escribirle a un cliente ni entre ellos. El
     * cliente no elige con quién habla, tiene «Atención al Cliente».
     */
    private Set<String> rolesQuePuedeContactar(Usuario usuario) {
        String rol = usuario.getRol().getNombre();
        if (ADMIN.equals(rol)) {
            return TODOS_LOS_ROLES;
        }
        if ("Cajero".equals(rol) || "Repartidor".equals(rol)) {
            return Set.of(ADMIN);
        }
        return Set.of();
    }

    // ------------------------------------------------------------------
    // Crear
    // ------------------------------------------------------------------

    @Transactional
    public ConversacionCreadaResponse crear(String email, CrearConversacionRequest request) {
        Usuario autor = usuarioRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        Conversacion conversacion = request.destinatarioId() == null
                ? soporteDe(autor, request.pedidoId())
                : privadaCon(autor, request.destinatarioId());
        MensajeResponse mensaje = enviarInterno(conversacion, autor,
                new EnviarMensajeRequest(request.clientMessageId(), request.mensaje()));
        return new ConversacionCreadaResponse(respuesta(conversacion, autor), mensaje);
    }

    /** «Atención al Cliente»: la abre el cliente y la atiende el mostrador. */
    private Conversacion soporteDe(Usuario autor, Long pedidoId) {
        if (esMostrador(autor)) {
            throw new BusinessException("chat.solo.cliente.inicia");
        }
        Pedido pedido = pedidoId == null ? null : pedidoPropio(autor, pedidoId);
        return conversacionRepository
                .findSoporteAbierto(autor.getId(), pedido == null ? null : pedido.getId(),
                        EstadoConversacion.ABIERTA)
                .orElseGet(() -> {
                    Conversacion nueva = new Conversacion();
                    nueva.setIniciador(autor);
                    nueva.setPedido(pedido);
                    nueva.setTipo(TipoConversacion.SOPORTE);
                    nueva.setEstado(EstadoConversacion.ABIERTA);
                    return conversacionRepository.save(nueva);
                });
    }

    /**
     * El chat privado con otra persona, reutilizando el que ya exista.
     *
     * <p>Reabre el existente aunque esté cerrado: dos personas que ya hablaron
     * siguen la misma conversación, en lugar de acumular hilos repetidos. Es lo
     * que pide «no crear conversaciones duplicadas si ya existe un chat entre
     * ambos», y lo respalda un índice único sobre el par.
     */
    private Conversacion privadaCon(Usuario autor, Long destinatarioId) {
        Usuario destinatario = usuarioRepository.findWithRolById(destinatarioId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "usuarios.no.encontrado", destinatarioId));
        if (destinatario.getId().equals(autor.getId())) {
            throw new BusinessException("chat.destinatario.uno.mismo");
        }
        if (!Boolean.TRUE.equals(destinatario.getActivo())
                || !rolesQuePuedeContactar(autor).contains(destinatario.getRol().getNombre())) {
            throw new AccessDeniedException("No puede iniciar una conversación con ese usuario");
        }
        return conversacionRepository.findPrivadaEntre(autor.getId(), destinatario.getId())
                .map(existente -> {
                    existente.setEstado(EstadoConversacion.ABIERTA);
                    return conversacionRepository.save(existente);
                })
                .orElseGet(() -> {
                    Conversacion nueva = new Conversacion();
                    nueva.setIniciador(autor);
                    nueva.setDestinatario(destinatario);
                    nueva.setTipo(TipoConversacion.PRIVADA);
                    nueva.setEstado(EstadoConversacion.ABIERTA);
                    return conversacionRepository.save(nueva);
                });
    }

    // ------------------------------------------------------------------
    // Leer
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ConversacionResponse> listar(String email, Pageable pageable) {
        Usuario usuario = usuario(email);
        var page = esMostrador(usuario)
                ? conversacionRepository.findParaMostrador(usuario.getId(), pageable)
                : conversacionRepository.findDondeParticipa(usuario.getId(), pageable);
        return PageResponse.from(page.map(conversacion -> respuesta(conversacion, usuario)));
    }

    @Transactional(readOnly = true)
    public ConversacionResponse obtener(Long id, String email) {
        Usuario usuario = usuario(email);
        return respuesta(exigirAcceso(id, usuario), usuario);
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
        long total = mensajeRepository.contarNoLeidosComoParte(usuario.getId());
        if (esMostrador(usuario)) {
            total += mensajeRepository.contarNoLeidosDeSoporte();
        }
        return new UnreadMessageCountResponse(total);
    }

    // ------------------------------------------------------------------
    // Escribir
    // ------------------------------------------------------------------

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
        int actualizados = atendiendoSoporte(conversacion, lector)
                ? mensajeRepository.marcarLeidosDelIniciador(
                        id, conversacion.getIniciador().getId(), ahora)
                : mensajeRepository.marcarLeidosPara(id, lector.getId(), ahora);
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

    private MensajeResponse enviarInterno(
            Conversacion conversacion, Usuario remitente, EnviarMensajeRequest request) {
        if (conversacion.getEstado() != EstadoConversacion.ABIERTA) {
            throw new BusinessException("chat.conversacion.cerrada");
        }
        String clientId = request.clientMessageId().trim();
        String contenido = request.contenido().trim();
        var existente = mensajeRepository.findByRemitenteIdAndClientMessageId(
                remitente.getId(), clientId);
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
        notificationPublisher.mensajeNuevo(
                guardado, destinatariosNotificacion(conversacion, remitente));
        realtime.mensaje(destinatarios(conversacion), response);
        return response;
    }

    // ------------------------------------------------------------------
    // Acceso
    // ------------------------------------------------------------------

    private Pedido pedidoPropio(Usuario cliente, Long id) {
        Pedido pedido = pedidoRepository.findWithDetallesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ventas.pedido.no.encontrado", id));
        if (pedido.getTipo() != TipoPedido.Venta
                || !pedido.getUsuario().getId().equals(cliente.getId())) {
            throw new AccessDeniedException("No puede asociar una conversación a ese pedido");
        }
        return pedido;
    }

    /**
     * La conversación, si el usuario tiene por qué verla.
     *
     * <p>Ser del mostrador abre el soporte, no las privadas ajenas: para esas
     * hay que ser una de las dos partes. Un administrador tampoco lee la
     * conversación privada de dos empleados.
     */
    private Conversacion exigirAcceso(Long id, Usuario usuario) {
        Conversacion conversacion = conversacionRepository.findParaAccesoById(id)
                .orElseThrow(() -> new ResourceNotFoundException("chat.conversacion.no.encontrada", id));
        if (conversacion.participa(usuario)) {
            return conversacion;
        }
        if (conversacion.getTipo() == TipoConversacion.SOPORTE && esMostrador(usuario)) {
            return conversacion;
        }
        throw new AccessDeniedException("No puede acceder a esa conversación");
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
    }

    private boolean esMostrador(Usuario usuario) {
        return ROLES_MOSTRADOR.contains(usuario.getRol().getNombre());
    }

    /** Quien atiende el soporte no es parte de la conversación, la contesta. */
    private boolean atendiendoSoporte(Conversacion conversacion, Usuario usuario) {
        return conversacion.getTipo() == TipoConversacion.SOPORTE
                && !conversacion.participa(usuario);
    }

    /** A qué sesiones se les empuja el mensaje por WebSocket. */
    private List<String> destinatarios(Conversacion conversacion) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        emails.add(conversacion.getIniciador().getEmail());
        if (conversacion.getDestinatario() != null) {
            emails.add(conversacion.getDestinatario().getEmail());
        } else {
            usuarioRepository.findByRolNombreInAndActivoTrue(ROLES_MOSTRADOR).stream()
                    .map(Usuario::getEmail).forEach(emails::add);
        }
        return List.copyOf(emails);
    }

    private List<Long> destinatariosNotificacion(Conversacion conversacion, Usuario remitente) {
        if (conversacion.getTipo() == TipoConversacion.PRIVADA) {
            Usuario otro = conversacion.contraparte(remitente);
            return otro == null ? List.of() : List.of(otro.getId());
        }
        if (esMostrador(remitente)) {
            return List.of(conversacion.getIniciador().getId());
        }
        return usuarioRepository.findByRolNombreInAndActivoTrue(ROLES_MOSTRADOR).stream()
                .map(Usuario::getId)
                .toList();
    }

    // ------------------------------------------------------------------
    // Presentación
    // ------------------------------------------------------------------

    private ConversacionResponse respuesta(Conversacion conversacion, Usuario solicitante) {
        MensajeResponse ultimo = mensajeRepository
                .findFirstByConversacionIdOrderByEnviadoEnDescIdDesc(conversacion.getId())
                .map(this::respuesta).orElse(null);
        long noLeidos = atendiendoSoporte(conversacion, solicitante)
                ? mensajeRepository.contarNoLeidosDelIniciador(
                        conversacion.getId(), conversacion.getIniciador().getId())
                : mensajeRepository.contarNoLeidosPara(conversacion.getId(), solicitante.getId());
        Usuario contraparte = conversacion.contraparte(solicitante);
        return new ConversacionResponse(
                conversacion.getId(),
                conversacion.getTipo().name(),
                conversacion.getEstado().name(),
                titulo(conversacion, solicitante, contraparte),
                contraparte == null ? null : contraparte.getRol().getNombre(),
                contraparte == null ? null : contraparte.getId(),
                contraparte == null ? null : contraparte.getEmail(),
                conversacion.getPedido() == null ? null : conversacion.getPedido().getId(),
                conversacion.getPedido() == null ? null : conversacion.getPedido().getNumero(),
                ultimo,
                noLeidos,
                conversacion.getUltimoMensajeEn(),
                conversacion.getCreatedAt(),
                conversacion.getUpdatedAt());
    }

    /**
     * Cómo se llama la conversación para quien la mira.
     *
     * <p>El cliente ve «Atención al Cliente» y nunca el nombre ni el rol de
     * quien responde: para él la tienda es una sola, no una lista de empleados.
     */
    private String titulo(Conversacion conversacion, Usuario solicitante, Usuario contraparte) {
        if (conversacion.getTipo() == TipoConversacion.SOPORTE) {
            Usuario iniciador = conversacion.getIniciador();
            return conversacion.participa(solicitante)
                    ? "Atención al Cliente"
                    : iniciador.getNombre() + " " + iniciador.getApellido();
        }
        return contraparte == null
                ? "Conversación"
                : contraparte.getNombre() + " " + contraparte.getApellido();
    }

    private MensajeResponse respuesta(Mensaje mensaje) {
        Usuario remitente = mensaje.getRemitente();
        return new MensajeResponse(mensaje.getId(), mensaje.getConversacion().getId(),
                mensaje.getClientMessageId(), mensaje.getTipo().name(), mensaje.getContenido(),
                remitente.getId(), remitente.getNombre() + " " + remitente.getApellido(),
                remitente.getEmail(), remitente.getRol().getNombre(),
                mensaje.getEnviadoEn(), mensaje.getEntregadoEn(), mensaje.getLeidoEn());
    }
}
