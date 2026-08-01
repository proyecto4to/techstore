package com.techstore.compras;

import com.techstore.comun.PageResponse;
import com.techstore.usuarios.CambioActivoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/proveedores")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('Admin','Cajero')")
@Tag(name = "Proveedores", description = "Gestión de proveedores para compras")
public class ProveedorController {

    private final ProveedorService proveedorService;

    @GetMapping
    @Operation(summary = "Listar proveedores (paginado)")
    public PageResponse<ProveedorResponse> listar(
            @ParameterObject @PageableDefault(size = 10, sort = "razonSocial", direction = Sort.Direction.ASC) Pageable pageable) {
        return proveedorService.listar(pageable);
    }

    @GetMapping("/activos")
    @Operation(summary = "Proveedores activos (para selección)")
    public List<ProveedorResponse> activos() {
        return proveedorService.activos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear proveedor")
    public ProveedorResponse crear(@Valid @RequestBody ProveedorRequest request) {
        return proveedorService.crear(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar proveedor")
    public ProveedorResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProveedorRequest request) {
        return proveedorService.actualizar(id, request);
    }

    @PutMapping("/{id}/activo")
    @Operation(summary = "Activar o desactivar proveedor")
    public ProveedorResponse cambiarActivo(@PathVariable Long id, @Valid @RequestBody CambioActivoRequest request) {
        return proveedorService.cambiarActivo(id, request.activo());
    }
}
