package com.techstore.catalogo;

import com.techstore.comun.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Catálogo de productos: lectura pública, escritura solo Admin")
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    @Operation(summary = "Listar productos",
            description = "Catálogo público paginado, sin costo, margen ni inventario interno.")
    public PageResponse<ProductoPublicResponse> listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long marcaId,
            @RequestParam(required = false) Boolean disponible,
            @ParameterObject @PageableDefault(size = 12, sort = "nombre") Pageable pageable) {
        return productoService.listarPublico(search, marcaId, disponible, pageable);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Listar productos con datos internos (Admin)")
    public PageResponse<ProductoResponse> listarAdmin(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long marcaId,
            @RequestParam(required = false, defaultValue = "false") boolean incluirInactivos,
            @ParameterObject @PageableDefault(size = 12, sort = "nombre") Pageable pageable) {
        return productoService.listarAdmin(search, marcaId, incluirInactivos, pageable);
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Obtener un producto con datos internos (Admin)")
    public ProductoResponse obtenerAdmin(@PathVariable Long id) {
        return productoService.obtenerAdmin(id);
    }

    @GetMapping("/reposicion")
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @Operation(summary = "Productos que llegaron a su punto de reposición (Admin/Cajero)",
            description = "Ordenados por urgencia. Solo aparecen los que tienen un stock mínimo definido.")
    public List<ProductoResponse> reposicion() {
        return productoService.reposicion();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un producto")
    public ProductoPublicResponse obtener(@PathVariable Long id) {
        return productoService.obtenerPublico(id);
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

    @PostMapping(value = "/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Subir foto de un producto (Admin)",
            description = "Reemplaza la imagen del producto por un archivo subido desde el equipo (JPG/PNG/WEBP/GIF, hasta 5MB)")
    public ProductoResponse subirImagen(@PathVariable Long id, @RequestPart("archivo") MultipartFile archivo) {
        return productoService.actualizarImagen(id, archivo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar producto (Admin)", description = "Baja lógica: el producto se desactiva, no se borra")
    public void eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
    }
}
