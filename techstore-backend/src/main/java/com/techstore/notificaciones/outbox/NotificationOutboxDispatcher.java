package com.techstore.notificaciones.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstore.envios.Envio;
import com.techstore.envios.EnvioRepository;
import com.techstore.facturacion.Factura;
import com.techstore.facturacion.FacturaRepository;
import com.techstore.facturacion.sifen.FacturaElectronicaService;
import com.techstore.mensajeria.Mensaje;
import com.techstore.mensajeria.MensajeRepository;
import com.techstore.notificaciones.CorreoRepository;
import com.techstore.notificaciones.CorreoService;
import com.techstore.notificaciones.EmailTemplateService;
import com.techstore.notificaciones.EstadoCorreo;
import com.techstore.notificaciones.PushDispatchSummary;
import com.techstore.notificaciones.PushNotificationService;
import com.techstore.notificaciones.NotificacionUsuarioService;
import com.techstore.notificaciones.TipoCorreo;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxDispatcher {
    private static final List<EstadoCorreo> EMAIL_TERMINAL =
            List.of(EstadoCorreo.ENVIADO, EstadoCorreo.SIMULADO);

    private final ObjectMapper objectMapper;
    private final PedidoRepository pedidoRepository;
    private final FacturaRepository facturaRepository;
    private final EnvioRepository envioRepository;
    private final MensajeRepository mensajeRepository;
    private final UsuarioRepository usuarioRepository;
    private final CorreoRepository correoRepository;
    private final CorreoService correoService;
    private final EmailTemplateService emailTemplateService;
    private final FacturaElectronicaService facturaElectronicaService;
    private final PushNotificationService pushNotificationService;
    private final NotificacionUsuarioService notificacionUsuarioService;

    public void dispatch(NotificationOutbox outbox) {
        NotificationPayload payload = payload(outbox);
        if (outbox.getConsumerChannel() == OutboxChannel.EMAIL) {
            dispatchEmail(outbox, payload);
        } else if (outbox.getConsumerChannel() == OutboxChannel.PUSH) {
            dispatchPush(outbox, payload);
        } else {
            dispatchInternal(outbox, payload);
        }
    }

    private void dispatchInternal(NotificationOutbox outbox, NotificationPayload payload) {
        if (payload.recipientUserId() == null) return;
        Content content = content(outbox.getEventType(), payload, false);
        notificacionUsuarioService.registrarDesdeOutbox(outbox, payload.recipientUserId(),
                content.pushTitle(), content.pushBody(), content.deepLink());
    }

    private void dispatchEmail(NotificationOutbox outbox, NotificationPayload payload) {
        TipoCorreo tipo = emailType(outbox.getEventType());
        String referencia = "OUTBOX-" + outbox.getId();
        if (correoRepository.existsByTipoAndReferenciaAndEstadoIn(tipo, referencia, EMAIL_TERMINAL)) {
            return;
        }
        Content content = content(outbox.getEventType(), payload, true);
        String html = emailTemplateService.construir(tipo, content.title(), content.recipientName(),
                content.message(), content.highlight(), content.button(), content.webUrl());
        EstadoCorreo estado = correoService.enviar(tipo, content.recipientEmail(), content.recipientName(),
                content.subject(), html, referencia, content.attachment(), content.attachmentName());
        if (estado == EstadoCorreo.ERROR) {
            throw new OutboxDeliveryException("El proveedor SMTP rechazó la entrega");
        }
    }

    private void dispatchPush(NotificationOutbox outbox, NotificationPayload payload) {
        if (payload.recipientUserId() == null) return;
        Content content = content(outbox.getEventType(), payload, false);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", outbox.getId().toString());
        data.put("eventType", outbox.getEventType().name());
        data.put("aggregateId", outbox.getAggregateId());
        if (payload.pedidoId() != null) data.put("pedidoId", payload.pedidoId());
        if (payload.conversacionId() != null) data.put("conversacionId", payload.conversacionId());
        PushDispatchSummary summary = pushNotificationService.notificarUsuario(
                payload.recipientUserId(), content.pushTitle(), content.pushBody(), content.deepLink(), data);
        if (summary.fallidos() > 0) {
            throw new OutboxDeliveryException("El proveedor push rechazó una o más entregas");
        }
    }

    private Content content(NotificationEventType event, NotificationPayload payload, boolean attachment) {
        return switch (event) {
            case PEDIDO_CREADO -> orderContent(event, requirePedido(payload.pedidoId()), payload);
            case PEDIDO_CONFIRMADO -> orderContent(event, requirePedido(payload.pedidoId()), payload);
            case PEDIDO_CANCELADO -> orderContent(event, requirePedido(payload.pedidoId()), payload);
            case PAGO_CONFIRMADO -> paymentContent(requireFactura(payload.facturaId()), payload, attachment);
            case PEDIDO_PREPARADO, ENVIO_DESPACHADO, ENVIO_EN_CAMINO, ENVIO_ENTREGADO ->
                    shippingContent(event, requireEnvio(payload.envioId()), payload);
            case MENSAJE_NUEVO -> messageContent(requireMensaje(payload.mensajeId()), payload);
            case REPARTO_ASIGNADO, REPARTO_REASIGNADO, REPARTO_ACTUALIZADO,
                    TRANSFERENCIA_SOLICITADA, TRANSFERENCIA_ACEPTADA, TRANSFERENCIA_RECHAZADA,
                    VEHICULO_ASIGNADO, VEHICULO_PROBLEMA -> operationContent(event, payload);
        };
    }

    private Content operationContent(NotificationEventType event, NotificationPayload payload) {
        Usuario user = requireUser(payload.recipientUserId());
        String title = payload.titulo() == null ? "Actualización de TechStore" : payload.titulo();
        String message = payload.mensaje() == null ? event.name() : payload.mensaje();
        String deepLink = payload.deepLink() == null ? "techstore://notificaciones" : payload.deepLink();
        return base(user, title, message, null, title + " — TechStore", title, message,
                null, null, deepLink);
    }

    private Content orderContent(NotificationEventType event, Pedido pedido, NotificationPayload payload) {
        Usuario user = requireUser(payload.recipientUserId());
        String numero = pedido.getNumero().toString();
        if (event == NotificationEventType.PEDIDO_CREADO) {
            return base(user, "¡Recibimos tu pedido!",
                    "Tu pedido N.º " + numero + " quedó pendiente y el stock está reservado a tu nombre.",
                    "Total: " + pedido.getTotal(), "Recibimos tu pedido N.º " + numero + " — TechStore",
                    "Pedido " + numero + " recibido", "Ya reservamos el stock de tu compra.",
                    "VER MIS PEDIDOS", emailTemplateService.url("/pedidos"),
                    "techstore://pedido/" + pedido.getId());
        }
        if (event == NotificationEventType.PEDIDO_CANCELADO) {
            return base(user, "Tu pedido fue cancelado",
                    "Cancelamos el pedido N.º " + numero + " y liberamos el stock reservado.",
                    "Estado: Cancelado", "Pedido N.º " + numero + " cancelado — TechStore",
                    "Pedido cancelado", "El pedido fue cancelado y el stock quedó liberado.",
                    "VER MIS PEDIDOS", emailTemplateService.url("/pedidos"),
                    "techstore://pedido/" + pedido.getId());
        }
        return base(user, "Tu pedido fue confirmado",
                "Confirmamos el pedido N.º " + numero + " y comenzamos a prepararlo.",
                "Estado: Confirmado", "Pedido N.º " + numero + " confirmado — TechStore",
                "Pedido confirmado", "Tu compra ya está en preparación.",
                "VER PEDIDO", emailTemplateService.url("/pedidos"),
                "techstore://pedido/" + pedido.getId());
    }

    private Content paymentContent(Factura factura, NotificationPayload payload, boolean withAttachment) {
        Pedido pedido = factura.getPedido();
        Usuario account = pedido.getUsuario();
        boolean externalRecipient = factura.getReceptorEmail() != null;
        String email = externalRecipient ? factura.getReceptorEmail() : account.getEmail();
        String name = externalRecipient
                ? (factura.getReceptorNombre() == null ? "cliente" : factura.getReceptorNombre())
                : fullName(account);
        byte[] pdf = null;
        if (withAttachment) {
            try {
                pdf = facturaElectronicaService.generarKude(factura.getId()).pdf();
            } catch (RuntimeException exception) {
                log.warn("No se pudo generar el KuDE de la factura {} para el outbox: {}",
                        factura.getNumero(), exception.getMessage());
            }
        }
        return new Content(email, name, "Pago confirmado",
                "Registramos el pago del pedido N.º " + pedido.getNumero()
                        + " y emitimos la factura " + factura.getNumero() + ".",
                "Total: " + factura.getTotal(), "Tu factura " + factura.getNumero() + " — TechStore",
                "Pago confirmado", "Tu factura ya está disponible.",
                externalRecipient ? null : "VER MIS PEDIDOS",
                externalRecipient ? null : emailTemplateService.url("/pedidos"),
                "techstore://pedido/" + pedido.getId(), pdf,
                pdf == null ? null : "factura-" + factura.getNumero() + ".pdf");
    }

    private Content shippingContent(NotificationEventType event, Envio envio, NotificationPayload payload) {
        Usuario user = requireUser(payload.recipientUserId());
        String numero = envio.getPedido().getNumero().toString();
        String title;
        String message;
        String push;
        switch (event) {
            case PEDIDO_PREPARADO -> {
                title = "Estamos preparando tu pedido";
                message = "El pedido N.º " + numero + " entró en preparación.";
                push = "Tu compra ya se está preparando.";
            }
            case ENVIO_DESPACHADO -> {
                title = "Tu pedido fue despachado";
                message = "Despachamos el pedido N.º " + numero + ". Ya podés seguir su recorrido.";
                push = "Tu pedido salió de TechStore.";
            }
            case ENVIO_EN_CAMINO -> {
                title = "Tu pedido está en camino";
                message = "El pedido N.º " + numero + " está en camino a su destino.";
                push = "Tu compra está cada vez más cerca.";
            }
            case ENVIO_ENTREGADO -> {
                title = "Pedido entregado";
                message = "Registramos la entrega del pedido N.º " + numero + ".";
                push = "Tu pedido fue entregado.";
            }
            default -> throw new IllegalArgumentException("Evento de envío no soportado");
        }
        String tracking = envio.getCodigoSeguimiento() == null
                ? "Estado: " + envio.getEstado() : "Seguimiento: " + envio.getCodigoSeguimiento();
        return new Content(user.getEmail(), fullName(user), title, message, tracking,
                title + " — TechStore", title, push, "VER SEGUIMIENTO",
                emailTemplateService.url("/pedidos"), "techstore://pedido/" + envio.getPedido().getId(),
                null, null);
    }

    private Content messageContent(Mensaje message, NotificationPayload payload) {
        Usuario user = requireUser(payload.recipientUserId());
        String sender = fullName(message.getRemitente());
        String excerpt = message.getContenido().replaceAll("[\\r\\n]+", " ").trim();
        if (excerpt.length() > 120) excerpt = excerpt.substring(0, 117) + "...";
        boolean staff = !"Cliente".equals(user.getRol().getNombre());
        String url = staff ? emailTemplateService.url("/admin/mensajes") : emailTemplateService.url("/");
        return new Content(user.getEmail(), fullName(user), "Tenés un nuevo mensaje",
                sender + " escribió en tu conversación de soporte.", excerpt,
                "Nuevo mensaje de " + sender + " — TechStore", "Nuevo mensaje de " + sender,
                excerpt, "ABRIR CONVERSACIÓN", url,
                "techstore://chat/" + message.getConversacion().getId(), null, null);
    }

    private Content base(Usuario user, String title, String message, String highlight, String subject,
                         String pushTitle, String pushBody, String button, String webUrl, String deepLink) {
        return new Content(user.getEmail(), fullName(user), title, message, highlight, subject,
                pushTitle, pushBody, button, webUrl, deepLink, null, null);
    }

    private TipoCorreo emailType(NotificationEventType event) {
        return switch (event) {
            case PEDIDO_CREADO -> TipoCorreo.PEDIDO;
            case PEDIDO_CONFIRMADO, PEDIDO_CANCELADO -> TipoCorreo.ESTADO_PEDIDO;
            case PAGO_CONFIRMADO -> TipoCorreo.FACTURA;
            case PEDIDO_PREPARADO, ENVIO_DESPACHADO, ENVIO_EN_CAMINO, ENVIO_ENTREGADO -> TipoCorreo.ENVIO;
            case MENSAJE_NUEVO -> TipoCorreo.MENSAJE;
            case REPARTO_ASIGNADO, REPARTO_REASIGNADO, REPARTO_ACTUALIZADO,
                    TRANSFERENCIA_SOLICITADA, TRANSFERENCIA_ACEPTADA, TRANSFERENCIA_RECHAZADA,
                    VEHICULO_ASIGNADO, VEHICULO_PROBLEMA -> TipoCorreo.ESTADO_PEDIDO;
        };
    }

    private NotificationPayload payload(NotificationOutbox outbox) {
        try {
            return objectMapper.readValue(outbox.getPayload(), NotificationPayload.class);
        } catch (JsonProcessingException exception) {
            throw new OutboxDeliveryException("El payload del evento no es válido");
        }
    }

    private Pedido requirePedido(Long id) {
        return pedidoRepository.findWithDetallesById(id)
                .orElseThrow(() -> new OutboxDeliveryException("El pedido del evento ya no existe"));
    }

    private Factura requireFactura(Long id) {
        return facturaRepository.findParaPdfById(id)
                .orElseThrow(() -> new OutboxDeliveryException("La factura del evento ya no existe"));
    }

    private Envio requireEnvio(Long id) {
        return envioRepository.findWithPedidoById(id)
                .orElseThrow(() -> new OutboxDeliveryException("El envío del evento ya no existe"));
    }

    private Mensaje requireMensaje(Long id) {
        return mensajeRepository.findForNotificationById(id)
                .orElseThrow(() -> new OutboxDeliveryException("El mensaje del evento ya no existe"));
    }

    private Usuario requireUser(Long id) {
        return usuarioRepository.findWithRolById(id)
                .orElseThrow(() -> new OutboxDeliveryException("El destinatario del evento ya no existe"));
    }

    private String fullName(Usuario user) {
        return user.getNombre() + " " + user.getApellido();
    }

    private record Content(
            String recipientEmail, String recipientName, String title, String message, String highlight,
            String subject, String pushTitle, String pushBody, String button, String webUrl, String deepLink,
            byte[] attachment, String attachmentName) { }
}
