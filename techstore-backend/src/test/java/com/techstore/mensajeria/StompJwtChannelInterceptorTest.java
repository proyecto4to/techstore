package com.techstore.mensajeria;

import com.techstore.usuarios.JwtService;
import com.techstore.usuarios.Rol;
import com.techstore.usuarios.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompJwtChannelInterceptorTest {
    private static final String SECRET = "clave-de-prueba-suficientemente-larga-para-hmac-sha!!";
    private final JwtService jwtService = new JwtService(SECRET, 900_000, 604_800_000);
    private final UserDetailsService users = mock(UserDetailsService.class);
    private final StompJwtChannelInterceptor interceptor = new StompJwtChannelInterceptor(jwtService, users);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void connectUsaAccessTokenFirmadoYConservaTenantEnLaSesion() {
        Usuario usuario = usuario();
        when(users.loadUserByUsername(usuario.getEmail())).thenReturn(User.withUsername(usuario.getEmail())
                .password("irrelevante").roles("Cliente").build());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + jwtService.generateAccessToken(usuario));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);

        Authentication authentication = assertInstanceOf(Authentication.class, accessor.getUser());
        assertEquals(usuario.getEmail(), authentication.getName());
        assertEquals(7L, assertInstanceOf(StompSessionDetails.class, authentication.getDetails()).tenantId());
    }

    @Test
    void connectRechazaRefreshTokenYConexionSinBearer() {
        Usuario usuario = usuario();
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                connect("Bearer " + jwtService.generateRefreshToken(usuario)), channel));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(connect(null), channel));
    }

    @Test
    void limitaEnviosYSuscripcionesALosDestinosDelChat() {
        var principal = UsernamePasswordAuthenticationToken.authenticated(
                "cliente@techstore.py", null, List.of());
        assertEquals("/app/chat/12", destinoPermitido(StompCommand.SEND, "/app/chat/12", principal));
        assertEquals("/user/queue/chat", destinoPermitido(
                StompCommand.SUBSCRIBE, "/user/queue/chat", principal));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                mensaje(StompCommand.SEND, "/queue/chat", principal), channel));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                mensaje(StompCommand.SUBSCRIBE, "/queue/global", principal), channel));
    }

    @Test
    void permiteEventosSoloDelTenantAutenticado() {
        var principal = UsernamePasswordAuthenticationToken.authenticated(
                "cliente@techstore.py", null, List.of());
        principal.setDetails(new StompSessionDetails(7L));
        assertEquals("/topic/tenant/7/events", destinoPermitido(
                StompCommand.SUBSCRIBE, "/topic/tenant/7/events", principal));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                mensaje(StompCommand.SUBSCRIBE, "/topic/tenant/8/events", principal), channel));
    }

    private Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) accessor.setNativeHeader("Authorization", authorization);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private String destinoPermitido(StompCommand command, String destination,
                                    UsernamePasswordAuthenticationToken principal) {
        interceptor.preSend(mensaje(command, destination, principal), channel);
        return destination;
    }

    private Message<byte[]> mensaje(StompCommand command, String destination,
                                    UsernamePasswordAuthenticationToken principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Usuario usuario() {
        Rol rol = new Rol();
        rol.setNombre("Cliente");
        Usuario usuario = new Usuario();
        usuario.setId(3L);
        usuario.setTenantId(7L);
        usuario.setEmail("cliente@techstore.py");
        usuario.setRol(rol);
        return usuario;
    }
}
