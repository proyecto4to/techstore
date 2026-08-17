package com.techstore.notificaciones;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dispositivos")
@RequiredArgsConstructor
@Tag(name = "Dispositivos móviles", description = "Registro seguro de instalaciones para push")
public class DispositivoMovilController {

    private final DispositivoMovilService service;

    @PutMapping("/actual")
    @Operation(summary = "Registrar o rotar el token push de esta instalación")
    public DispositivoMovilResponse registrar(@Valid @RequestBody RegistroDispositivoRequest request,
                                               Authentication auth) {
        return service.registrar(auth.getName(), request);
    }

    @GetMapping
    @Operation(summary = "Listar mis instalaciones activas sin exponer sus push tokens")
    public List<DispositivoMovilResponse> listar(Authentication auth) {
        return service.listar(auth.getName());
    }

    @DeleteMapping("/actual/{deviceIdentifier}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desactivar esta instalación al cerrar sesión")
    public void desactivar(@PathVariable String deviceIdentifier, Authentication auth) {
        service.desactivar(auth.getName(), deviceIdentifier);
    }
}
