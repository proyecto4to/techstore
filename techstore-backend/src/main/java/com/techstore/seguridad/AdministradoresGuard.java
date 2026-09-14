package com.techstore.seguridad;

import com.techstore.comun.BusinessException;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Impide que el sistema se quede sin nadie que pueda administrarlo.
 *
 * <p>Es la única forma de romper esto sin arreglo posible desde la interfaz:
 * desactivar al último administrador, cambiarle el rol o quitarle el acceso a
 * Seguridad deja una instalación que nadie puede volver a abrir. No hay
 * pantalla para salir de ahí —habría que entrar a la base de datos a mano—, así
 * que se comprueba antes y se rechaza.
 *
 * <p>«Administrador con mando» es alguien que además de tener el rol puede
 * usarlo: está activo y no tiene una restricción individual sobre Seguridad. Un
 * administrador desactivado no sirve para recuperar el sistema, y uno al que se
 * le restringió Seguridad, tampoco.
 */
@Service
@RequiredArgsConstructor
public class AdministradoresGuard {

    private final UsuarioRepository usuarioRepository;
    private final PermisoUsuarioRepository permisosUsuario;

    /** Quiénes pueden hoy entrar y repartir permisos. */
    @Transactional(readOnly = true)
    public List<Usuario> conMando() {
        return usuarioRepository
                .findByRolNombreOrderByNombreAscApellidoAsc(RolesDelSistema.ADMINISTRADOR).stream()
                .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()))
                .filter(usuario -> sinRestriccionDeSeguridad(usuario.getId()))
                .toList();
    }

    /**
     * Rechaza la operación si dejaría al sistema sin administradores.
     *
     * @param usuarioId    a quién se le está por sacar el mando
     * @param queSeIntenta en infinitivo y en castellano: entra tal cual en el
     *                     mensaje que lee la persona
     */
    @Transactional(readOnly = true)
    public void verificarQueNoEsElUltimo(Long usuarioId, String queSeIntenta) {
        List<Usuario> administradores = conMando();
        boolean loPierde = administradores.stream()
                .anyMatch(usuario -> usuario.getId().equals(usuarioId));
        if (loPierde && administradores.size() <= 1) {
            throw new BusinessException("seguridad.ultimo.administrador", queSeIntenta);
        }
    }

    private boolean sinRestriccionDeSeguridad(Long usuarioId) {
        return permisosUsuario.findByUsuarioIdOrderByModuloAscAccionAsc(usuarioId).stream()
                .noneMatch(permiso -> permiso.getModulo() == Modulo.SEGURIDAD
                        && !Boolean.TRUE.equals(permiso.getPermitido()));
    }
}
