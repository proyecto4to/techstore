package com.techstore.notificaciones;

import com.techstore.comun.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
@Tag(name = "Centro de notificaciones", description = "Avisos persistentes del usuario autenticado")
public class NotificacionUsuarioController {
    private final NotificacionUsuarioService service;

    @GetMapping
    @Operation(summary = "Listar mis notificaciones, más recientes primero")
    public PageResponse<NotificacionUsuarioResponse> listar(
            Authentication auth,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.listar(auth.getName(), pageable);
    }

    @GetMapping("/no-leidas")
    @Operation(summary = "Contar mis notificaciones no leídas")
    public UnreadNotificationCountResponse contarNoLeidas(Authentication auth) {
        return service.contarNoLeidas(auth.getName());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una notificación propia")
    public NotificacionUsuarioResponse obtener(@PathVariable Long id, Authentication auth) {
        return service.obtener(id, auth.getName());
    }

    @PutMapping("/{id}/leida")
    @Operation(summary = "Marcar una notificación propia como leída")
    public NotificacionUsuarioResponse marcarLeida(@PathVariable Long id, Authentication auth) {
        return service.marcarLeida(id, auth.getName());
    }

    @PutMapping("/leidas")
    @Operation(summary = "Marcar todas mis notificaciones como leídas")
    public MarkNotificationsReadResponse marcarTodasLeidas(Authentication auth) {
        return service.marcarTodasLeidas(auth.getName());
    }
}
