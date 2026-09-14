package com.techstore.seguridad;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.usuarios.Rol;
import com.techstore.usuarios.RolRepository;
import com.techstore.usuarios.UsuarioAdminResponse;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Alta, edición y baja lógica de roles.
 *
 * <p>Dos cosas que este servicio deliberadamente no hace:
 *
 * <ul>
 *   <li><b>No renombra.</b> El nombre del rol es lo que viaja en el token y lo
 *       que comparan las anotaciones {@code hasRole(...)} de los controladores.
 *       Cambiarlo desde una grilla dejaría a las dos capas hablando de roles
 *       distintos y a la gente sin permisos, sin ningún error visible. Para
 *       corregir un nombre se crea el rol nuevo y se reasigna.
 *   <li><b>No borra.</b> Las facturas y la auditoría guardan con qué rol se
 *       hizo cada cosa; borrarlo dejaría ese historial hablando de algo que ya
 *       no existe. Se desactiva, que saca al rol de las listas de asignación y
 *       no le cambia nada a quien ya lo tiene.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RolAdminService {

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final PermisoRolRepository permisoRolRepository;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<RolDetalleResponse> listar() {
        return rolRepository.findAllByOrderByNombreAsc().stream()
                .map(rol -> RolDetalleResponse.from(rol, usuarioRepository.countByRolNombre(rol.getNombre())))
                .toList();
    }

    /**
     * Crea un rol sin ningún permiso.
     *
     * <p>Arranca con toda la matriz en falso a propósito. La regla general del
     * sistema es que una combinación sin cargar queda permitida —así se activó
     * la capa sin cambiarle el trabajo a nadie—, pero eso vale para lo que ya
     * venía funcionando. Un rol que se crea hoy no tiene nada que preservar, y
     * darle todo por omisión sería lo contrario del mínimo privilegio: quien lo
     * crea le habilita lo que necesita.
     */
    @Transactional
    public RolDetalleResponse crear(String nombre, String descripcion) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (rolRepository.existsByNombreIgnoreCase(limpio)) {
            throw new BusinessException("seguridad.rol.duplicado", limpio);
        }
        Rol rol = new Rol();
        rol.setNombre(limpio);
        rol.setDescripcion(descripcion == null || descripcion.isBlank() ? null : descripcion.trim());
        rol.setActivo(true);
        Rol guardado = rolRepository.save(rol);

        for (Modulo modulo : Modulo.values()) {
            for (AccionPermiso accion : AccionPermiso.values()) {
                PermisoRol permiso = new PermisoRol();
                permiso.setRol(guardado);
                permiso.setModulo(modulo);
                permiso.setAccion(accion);
                permiso.setPermitido(false);
                permisoRolRepository.save(permiso);
            }
        }

        auditoria.registrar("Rol", guardado.getId(), AuditoriaService.Accion.Crear,
                "Creó el rol %s, sin permisos".formatted(guardado.getNombre()),
                AuditoriaService.Cambios.nuevo().valor("rol", guardado.getNombre()));

        return RolDetalleResponse.from(guardado, 0);
    }

    /** Cambia la descripción o lo activa y desactiva. El nombre no se toca. */
    @Transactional
    public RolDetalleResponse editar(String nombre, String descripcion, Boolean activo) {
        Rol rol = porNombre(nombre);
        long usuarios = usuarioRepository.countByRolNombre(rol.getNombre());

        if (Boolean.FALSE.equals(activo)) {
            if (RolesDelSistema.esAdministrador(rol.getNombre())) {
                throw new BusinessException("seguridad.rol.admin.protegido");
            }
            long activos = usuarioRepository.countByRolNombreAndActivoTrue(rol.getNombre());
            if (activos > 0) {
                throw new BusinessException("seguridad.rol.con.usuarios", rol.getNombre(), activos);
            }
        }

        String descripcionAnterior = rol.getDescripcion();
        boolean activoAnterior = Boolean.TRUE.equals(rol.getActivo());
        if (descripcion != null) {
            rol.setDescripcion(descripcion.isBlank() ? null : descripcion.trim());
        }
        if (activo != null) {
            rol.setActivo(activo);
        }
        Rol guardado = rolRepository.save(rol);

        auditoria.registrar("Rol", guardado.getId(), AuditoriaService.Accion.Editar,
                "Editó el rol " + guardado.getNombre(),
                AuditoriaService.Cambios.nuevo()
                        .de("descripcion", descripcionAnterior, guardado.getDescripcion())
                        .de("activo", activoAnterior, Boolean.TRUE.equals(guardado.getActivo())));

        return RolDetalleResponse.from(guardado, usuarios);
    }

    /** Quiénes tienen este rol, para ver a quién le cambia la vida tocarlo. */
    @Transactional(readOnly = true)
    public List<UsuarioAdminResponse> usuarios(String nombre) {
        Rol rol = porNombre(nombre);
        return usuarioRepository.findByRolNombreOrderByNombreAscApellidoAsc(rol.getNombre()).stream()
                .map(UsuarioAdminResponse::from)
                .toList();
    }

    private Rol porNombre(String nombre) {
        return rolRepository.findByNombreIgnoreCase(nombre == null ? "" : nombre.trim())
                .orElseThrow(() -> new ResourceNotFoundException("seguridad.rol.no.encontrado", nombre));
    }
}
