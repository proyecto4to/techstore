package com.techstore.promociones;

import com.techstore.comun.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración de promociones.
 *
 * <p>Vive bajo el módulo Productos a efectos de permisos (ver
 * {@code PermisoInterceptor}): una promoción es el precio de un producto, y
 * quien puede cambiar precios puede descontarlos. No hay endpoint público: el
 * descuento le llega a quien compra dentro del precio del catálogo, ya
 * resuelto por el servidor.
 */
@RestController
@RequestMapping("/api/v1/promociones")
@RequiredArgsConstructor
@Tag(name = "Promociones", description = "Descuentos por tiempo limitado sobre el catálogo")
public class PromocionController {

    private final PromocionService service;

    @GetMapping
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Listar promociones")
    public PageResponse<PromocionResponse> listar(
            @ParameterObject @PageableDefault(size = 20, sort = "desde") Pageable pageable) {
        return service.listar(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Ver una promoción")
    public PromocionResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('Admin')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una promoción")
    public PromocionResponse crear(@Valid @RequestBody PromocionRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Editar una promoción")
    public PromocionResponse editar(@PathVariable Long id, @Valid @RequestBody PromocionRequest request) {
        return service.editar(id, request);
    }

    @PutMapping("/{id}/activa")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Encender o apagar una promoción sin editarla")
    public PromocionResponse cambiarEstado(@PathVariable Long id, @RequestParam boolean activa) {
        return service.cambiarEstado(id, activa);
    }
}
