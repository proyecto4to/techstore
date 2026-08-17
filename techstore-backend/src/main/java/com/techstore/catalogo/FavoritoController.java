package com.techstore.catalogo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/favoritos")
@RequiredArgsConstructor
@Tag(name = "Favoritos", description = "Productos guardados por el usuario autenticado")
public class FavoritoController {

    private final FavoritoService favoritoService;

    @GetMapping
    @Operation(summary = "Listar mis favoritos visibles")
    public List<FavoritoResponse> listar(Authentication auth) {
        return favoritoService.listar(auth.getName());
    }

    @PostMapping("/{productoId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Guardar un producto como favorito")
    public FavoritoResponse agregar(@PathVariable Long productoId, Authentication auth) {
        return favoritoService.agregar(auth.getName(), productoId);
    }

    @DeleteMapping("/{productoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar un producto de mis favoritos")
    public void quitar(@PathVariable Long productoId, Authentication auth) {
        favoritoService.quitar(auth.getName(), productoId);
    }
}
