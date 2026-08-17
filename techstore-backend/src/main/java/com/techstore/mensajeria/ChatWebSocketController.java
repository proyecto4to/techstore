package com.techstore.mensajeria;

import com.techstore.comun.ExcepcionTraducible;
import com.techstore.comun.Mensajes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {
    private final MensajeriaService service;
    private final Mensajes mensajes;

    @MessageMapping("/chat/{conversationId}")
    public void enviar(@DestinationVariable Long conversationId,
                       @Valid @Payload EnviarMensajeRequest request,
                       Principal principal) {
        service.enviar(conversationId, principal.getName(), request);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/chat-errors")
    public ChatErrorResponse error(Exception ex) {
        if (ex instanceof ExcepcionTraducible traducible) {
            return ChatErrorResponse.de(traducible.getClave(),
                    mensajes.de(traducible.getClave(), traducible.getArgumentos()));
        }
        if (ex instanceof AccessDeniedException) {
            return ChatErrorResponse.de("chat.acceso.denegado", mensajes.de("error.sin.permisos"));
        }
        log.warn("Error STOMP controlado sin detalle para el cliente", ex);
        return ChatErrorResponse.de("chat.error", mensajes.de("error.interno"));
    }
}
