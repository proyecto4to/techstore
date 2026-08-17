package com.techstore.direcciones;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/direcciones")
@RequiredArgsConstructor
@Tag(name = "Direcciones", description = "Libreta de direcciones del usuario autenticado")
public class DireccionEntregaController {

    private final DireccionEntregaService direccionService;

    @GetMapping
    @Operation(summary = "Listar mis direcciones")
    public List<DireccionEntregaResponse> listar(Authentication auth) {
        return direccionService.listar(auth.getName());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una dirección propia")
    public DireccionEntregaResponse obtener(@PathVariable Long id, Authentication auth) {
        return direccionService.obtener(auth.getName(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una dirección")
    public DireccionEntregaResponse crear(@Valid @RequestBody DireccionEntregaRequest request,
                                          Authentication auth) {
        return direccionService.crear(auth.getName(), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una dirección propia")
    public DireccionEntregaResponse actualizar(@PathVariable Long id,
                                               @Valid @RequestBody DireccionEntregaRequest request,
                                               Authentication auth) {
        return direccionService.actualizar(auth.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar una dirección propia")
    public void eliminar(@PathVariable Long id, Authentication auth) {
        direccionService.eliminar(auth.getName(), id);
    }
}
