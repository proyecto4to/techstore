package com.techstore.catalogo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/marcas")
@RequiredArgsConstructor
@Tag(name = "Marcas", description = "Marcas activas para filtrar el catálogo")
public class MarcaController {

    private final MarcaRepository marcaRepository;

    @GetMapping
    @SecurityRequirements
    @Transactional(readOnly = true)
    @Operation(summary = "Listar marcas activas")
    public List<MarcaResponse> listar() {
        return marcaRepository.findByActivoTrueOrderByNombre().stream()
                .map(MarcaResponse::from)
                .toList();
    }
}
