package com.techstore.mensajeria;

import com.techstore.comun.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversaciones")
@RequiredArgsConstructor
@Tag(name = "Mensajería", description = "Conversaciones recuperables y mensajes idempotentes")
public class ConversacionController {
    private final MensajeriaService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear o reutilizar una conversación abierta")
    public ConversacionCreadaResponse crear(@Valid @RequestBody CrearConversacionRequest request, Authentication auth) {
        return service.crear(auth.getName(), request);
    }

    @GetMapping
    @Operation(summary = "Listar conversaciones propias o inbox de Staff")
    public PageResponse<ConversacionResponse> listar(
            Authentication auth,
            @ParameterObject @PageableDefault(size = 20, sort = "ultimoMensajeEn", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.listar(auth.getName(), pageable);
    }

    @GetMapping("/{id}")
    public ConversacionResponse obtener(@PathVariable Long id, Authentication auth) {
        return service.obtener(id, auth.getName());
    }

    @GetMapping("/no-leidos")
    @Operation(summary = "Contar mensajes no leídos para el badge")
    public UnreadMessageCountResponse contarNoLeidos(Authentication auth) {
        return service.contarNoLeidos(auth.getName());
    }

    @GetMapping("/{id}/mensajes")
    @Operation(summary = "Recuperar historial paginado, más reciente primero")
    public PageResponse<MensajeResponse> historial(
            @PathVariable Long id,
            Authentication auth,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return service.historial(id, auth.getName(), pageable);
    }

    @PostMapping("/{id}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enviar por REST con clientMessageId idempotente")
    public MensajeResponse enviar(@PathVariable Long id, @Valid @RequestBody EnviarMensajeRequest request,
                                  Authentication auth) {
        return service.enviar(id, auth.getName(), request);
    }

    @PutMapping("/{id}/leido")
    public LecturaResponse marcarLeido(@PathVariable Long id, Authentication auth) {
        return service.marcarLeido(id, auth.getName());
    }

    @PutMapping("/{id}/cerrar")
    public ConversacionResponse cerrar(@PathVariable Long id, Authentication auth) {
        return service.cerrar(id, auth.getName());
    }
}
