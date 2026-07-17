package com.techstore.controller;

import com.techstore.dto.request.ProductoRequest;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.ProductoResponse;
import com.techstore.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Catálogo de productos: lectura pública, escritura solo Admin")
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    @Operation(summary = "Listar productos",
            description = "Catálogo paginado con búsqueda por nombre y filtro por marca. "
                    + "incluirInactivos=true solo tiene efecto para un Admin autenticado.")
    public PageResponse<ProductoResponse> listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long marcaId,
            @RequestParam(required = false, defaultValue = "false") boolean incluirInactivos,
            Authentication auth,
            @ParameterObject @PageableDefault(size = 12, sort = "nombre") Pageable pageable) {
        return productoService.listar(search, marcaId, incluirInactivos && esAdmin(auth), pageable);
    }

    private boolean esAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_Admin".equals(a.getAuthority()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un producto")
    public ProductoResponse obtener(@PathVariable Long id) {
        return productoService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('Admin')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear producto (Admin)")
    public ProductoResponse crear(@Valid @RequestBody ProductoRequest request) {
        return productoService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Actualizar producto (Admin)")
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return productoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar producto (Admin)", description = "Baja lógica: el producto se desactiva, no se borra")
    public void eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
    }
}
