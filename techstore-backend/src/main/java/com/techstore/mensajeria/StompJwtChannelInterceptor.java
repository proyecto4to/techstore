package com.techstore.mensajeria;

import com.techstore.comun.tenant.TenantContext;
import com.techstore.usuarios.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompJwtChannelInterceptor implements ChannelInterceptor, ExecutorChannelInterceptor {
    private static final String BEARER = "Bearer ";
    private static final Pattern DESTINO_ENVIO = Pattern.compile("/app/chat/[1-9][0-9]*");
    private static final Set<String> DESTINOS_SUSCRIPCION = Set.of(
            "/user/queue/chat", "/user/queue/chat-read", "/user/queue/chat-errors");

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            autenticar(accessor);
        } else if (accessor.getCommand() != null && accessor.getUser() == null) {
            throw new MessageDeliveryException("Sesión STOMP no autenticada");
        } else if (StompCommand.SEND.equals(accessor.getCommand())
                && !DESTINO_ENVIO.matcher(String.valueOf(accessor.getDestination())).matches()) {
            throw new MessageDeliveryException("Destino STOMP no permitido");
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                && !DESTINOS_SUSCRIPCION.contains(accessor.getDestination())) {
            throw new MessageDeliveryException("Suscripción STOMP no permitida");
        }
        return message;
    }

    private void autenticar(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) header = accessor.getFirstNativeHeader("authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessageDeliveryException("Falta el access token STOMP");
        }
        try {
            Claims claims = jwtService.parseToken(header.substring(BEARER.length()));
            if (!jwtService.isAccessToken(claims)) throw new MessageDeliveryException("Token STOMP inválido");
            Long tenantId = tenantId(claims);
            UserDetails details = TenantContext.comoTenant(tenantId,
                    () -> userDetailsService.loadUserByUsername(claims.getSubject()));
            if (!details.isEnabled()) throw new MessageDeliveryException("Cuenta deshabilitada");
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    details, null, details.getAuthorities());
            auth.setDetails(new StompSessionDetails(tenantId));
            accessor.setUser(auth);
        } catch (JwtException | UsernameNotFoundException ex) {
            log.debug("Conexión STOMP rechazada: {}", ex.getMessage());
            throw new MessageDeliveryException("Access token STOMP inválido");
        }
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;
        if (accessor.getUser() instanceof Authentication auth
                && auth.getDetails() instanceof StompSessionDetails details) {
            TenantContext.fijar(details.tenantId());
        }
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel,
                                    MessageHandler handler, Exception ex) {
        TenantContext.limpiar();
    }

    private Long tenantId(Claims claims) {
        Object value = claims.get(JwtService.CLAIM_TENANT);
        long tenant = value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        if (tenant <= 0) throw new MessageDeliveryException("Tenant STOMP inválido");
        return tenant;
    }
}
