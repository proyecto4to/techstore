package com.techstore.seguridad;

import com.techstore.usuarios.UsuarioAdminResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administración de roles, permisos y acceso a pantallas.
 *
 * <p>Solo Admin: quien puede repartir permisos puede dárselos a sí mismo, así
 * que el módulo que los administra tiene que ser el más cerrado de todos. La
 * anotación está en la clase, no en cada método, para que agregar un endpoint
 * acá no pueda olvidarse de cerrarlo.
 *
 * <p>Lo que cada persona puede hacer <b>para sí misma</b> no se pregunta acá
 * sino en {@code /api/v1/auth/mis-permisos}: es su propia sesión, no
 * administración, y tiene que responder aunque el rol no toque Seguridad.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/seguridad")
@PreAuthorize("hasRole('Admin')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Seguridad", description = "Usuarios, roles, permisos, módulos y acciones")
public class SeguridadController {

    private final PermisoService service;
    private final RolAdminService roles;

    // ------------------------------------------------------------------
    // Catálogo: qué se puede configurar
    // ------------------------------------------------------------------

    @GetMapping("/catalogo")
    @Operation(summary = "Módulos, acciones y pantallas disponibles",
            description = "Con esto se dibuja la pantalla de permisos. Agregar un módulo en el "
                    + "backend lo hace aparecer en la Web y en el APK sin tocar ninguno de los dos")
    public CatalogoSeguridadResponse catalogo() {
        return service.catalogo();
    }

    // ------------------------------------------------------------------
    // Roles
    // ------------------------------------------------------------------

    @GetMapping("/roles")
    @Operation(summary = "Los roles que se pueden configurar")
    public List<String> roles() {
        return service.roles();
    }

    @GetMapping("/roles/detalle")
    @Operation(summary = "Los roles con su descripción, su estado y cuánta gente los tiene")
    public List<RolDetalleResponse> rolesDetallados() {
        return roles.listar();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un rol",
            description = "Nace sin ningún permiso: quien lo crea le habilita lo que necesita")
    public RolDetalleResponse crearRol(@Valid @RequestBody CrearRolRequest request) {
        return roles.crear(request.nombre(), request.descripcion());
    }

    @PutMapping("/roles/{rol}")
    @Operation(summary = "Editar la descripción de un rol o activarlo y desactivarlo",
            description = "El nombre no se cambia: es lo que comparan las anotaciones del backend. "
                    + "Un rol con gente activa no se puede desactivar")
    public RolDetalleResponse editarRol(
            @PathVariable String rol, @Valid @RequestBody EditarRolRequest request) {
        return roles.editar(rol, request.descripcion(), request.activo());
    }

    @GetMapping("/roles/{rol}/usuarios")
    @Operation(summary = "Quiénes tienen este rol")
    public List<UsuarioAdminResponse> usuariosDelRol(@PathVariable String rol) {
        return roles.usuarios(rol);
    }

    // ------------------------------------------------------------------
    // Permisos del rol
    // ------------------------------------------------------------------

    @GetMapping("/roles/{rol}/permisos")
    @Operation(summary = "Matriz de permisos y pantallas de un rol")
    public MatrizPermisosResponse matriz(@PathVariable String rol) {
        return service.matriz(rol);
    }

    @PutMapping("/roles/{rol}/permisos")
    @Operation(summary = "Cambia un permiso del rol",
            description = "Solo puede restringir: destildar cierra, tildar no abre lo que el backend ya niega")
    public MatrizPermisosResponse guardarPermiso(
            @PathVariable String rol,
            @Valid @RequestBody CambioPermisoRequest request) {
        service.guardarPermiso(rol, request.modulo(), request.accion(), request.permitido());
        return service.matriz(rol);
    }

    @PutMapping("/roles/{rol}/pantallas")
    @Operation(summary = "Habilita o deshabilita una pantalla para el rol")
    public MatrizPermisosResponse guardarPantalla(
            @PathVariable String rol,
            @Valid @RequestBody CambioPantallaRequest request) {
        service.guardarPantalla(rol, request.pantalla(), request.permitido());
        return service.matriz(rol);
    }

    // ------------------------------------------------------------------
    // Permisos de una persona
    // ------------------------------------------------------------------

    @GetMapping("/usuarios/{id}/permisos")
    @Operation(summary = "Qué puede hacer realmente esta persona",
            description = "Cada acción viene con su origen: heredada del rol, asignada "
                    + "individualmente o restringida individualmente")
    public PermisosEfectivosResponse permisosDelUsuario(@PathVariable Long id) {
        return service.efectivos(id);
    }

    @PutMapping("/usuarios/{id}/permisos")
    @Operation(summary = "Conceder o restringir una acción a esta persona",
            description = "La excepción pesa más que el rol. Restringir Seguridad al último "
                    + "administrador, o a uno mismo, se rechaza")
    public PermisosEfectivosResponse guardarPermisoDelUsuario(
            @PathVariable Long id,
            @Valid @RequestBody CambioPermisoUsuarioRequest request,
            Authentication auth) {
        return service.guardarPermisoUsuario(id, request.modulo(), request.accion(),
                request.permitido(), request.motivo(), auth.getName());
    }

    @DeleteMapping("/usuarios/{id}/permisos/{modulo}/{accion}")
    @Operation(summary = "Quitar la excepción: vuelve a valer lo que diga el rol")
    public PermisosEfectivosResponse quitarPermisoDelUsuario(
            @PathVariable Long id,
            @PathVariable Modulo modulo,
            @PathVariable AccionPermiso accion) {
        return service.quitarPermisoUsuario(id, modulo, accion);
    }

    // ------------------------------------------------------------------

    public record CambioPermisoRequest(
            @NotNull Modulo modulo,
            @NotNull AccionPermiso accion,
            @NotNull Boolean permitido) { }

    public record CambioPantallaRequest(
            @NotBlank @Size(max = 60) String pantalla,
            @NotNull Boolean permitido) { }

    /**
     * @param permitido true concede, false restringe
     * @param motivo    por qué; se muestra al lado del permiso y queda auditado
     */
    public record CambioPermisoUsuarioRequest(
            @NotNull Modulo modulo,
            @NotNull AccionPermiso accion,
            @NotNull Boolean permitido,
            @Size(max = 200) String motivo) { }

    public record CrearRolRequest(
            @NotBlank @Size(max = 50) String nombre,
            @Size(max = 500) String descripcion) { }

    public record EditarRolRequest(
            @Size(max = 500) String descripcion,
            Boolean activo) { }
}
