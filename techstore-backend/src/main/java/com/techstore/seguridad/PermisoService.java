package com.techstore.seguridad;

import com.techstore.comun.ResourceNotFoundException;
import com.techstore.usuarios.Rol;
import com.techstore.usuarios.RolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Los permisos por rol, guardados en la base y no escritos en el código.
 *
 * <p><b>Solo restringen.</b> Las anotaciones {@code @PreAuthorize} de cada
 * controlador siguen siendo el piso y esta capa se aplica encima, así que
 * marcar una casilla acá nunca abre un endpoint que el código considera
 * cerrado; destildarla sí lo cierra. Se eligió así porque lo contrario
 * convertiría una pantalla de administración en una forma de saltearse las
 * reglas de negocio: que alguien pueda darse permiso de vaciar la caja desde
 * una grilla no es parametrizar, es un agujero.
 *
 * <p>Cuando una combinación no está cargada, se responde permitido: el sistema
 * funcionaba sin esta tabla y su ausencia no puede dejar a nadie afuera. Quien
 * quiera negar algo lo destilda explícitamente.
 */
@Service
@RequiredArgsConstructor
public class PermisoService {

    private final PermisoRolRepository permisoRepository;
    private final AccesoPantallaRolRepository accesoRepository;
    private final RolRepository rolRepository;

    /** Si ese rol puede hacer esa acción sobre ese módulo. */
    @Transactional(readOnly = true)
    public boolean puede(String rolNombre, Modulo modulo, AccionPermiso accion) {
        if (rolNombre == null) {
            return false;
        }
        return permisoRepository
                .findByRolNombreAndModuloAndAccion(rolNombre, modulo, accion)
                .map(PermisoRol::getPermitido)
                .orElse(true);
    }

    /** Si ese rol tiene habilitada esa pantalla. */
    @Transactional(readOnly = true)
    public boolean veLaPantalla(String rolNombre, String pantalla) {
        if (rolNombre == null) {
            return false;
        }
        return accesoRepository
                .findByRolNombreAndPantalla(rolNombre, pantalla)
                .map(AccesoPantallaRol::getPermitido)
                .orElse(true);
    }

    /** La matriz completa de un rol, para la pantalla de Seguridad. */
    @Transactional(readOnly = true)
    public MatrizPermisosResponse matriz(String rolNombre) {
        Rol rol = rolPorNombre(rolNombre);
        List<PermisoRol> guardados = permisoRepository
                .findByRolNombreOrderByModuloAscAccionAsc(rol.getNombre());
        List<MatrizPermisosResponse.FilaModulo> filas = new ArrayList<>();
        for (Modulo modulo : Modulo.values()) {
            List<MatrizPermisosResponse.Casilla> casillas = new ArrayList<>();
            for (AccionPermiso accion : AccionPermiso.values()) {
                boolean permitido = guardados.stream()
                        .filter(p -> p.getModulo() == modulo && p.getAccion() == accion)
                        .findFirst()
                        .map(PermisoRol::getPermitido)
                        .orElse(true);
                casillas.add(new MatrizPermisosResponse.Casilla(
                        accion.name(), accion.etiqueta(), permitido));
            }
            filas.add(new MatrizPermisosResponse.FilaModulo(
                    modulo.name(), modulo.etiqueta(), casillas));
        }
        return new MatrizPermisosResponse(rol.getNombre(), filas, pantallas(rol.getNombre()));
    }

    private List<MatrizPermisosResponse.Pantalla> pantallas(String rolNombre) {
        return accesoRepository.findByRolNombreOrderByPantallaAsc(rolNombre).stream()
                .map(a -> new MatrizPermisosResponse.Pantalla(a.getPantalla(), a.getPermitido()))
                .toList();
    }

    /** Cambia una casilla de la matriz. */
    @Transactional
    public void guardarPermiso(String rolNombre, Modulo modulo, AccionPermiso accion, boolean permitido) {
        Rol rol = rolPorNombre(rolNombre);
        PermisoRol permiso = permisoRepository
                .findByRolNombreAndModuloAndAccion(rol.getNombre(), modulo, accion)
                .orElseGet(() -> {
                    PermisoRol nuevo = new PermisoRol();
                    nuevo.setRol(rol);
                    nuevo.setModulo(modulo);
                    nuevo.setAccion(accion);
                    return nuevo;
                });
        permiso.setPermitido(permitido);
        permisoRepository.save(permiso);
    }

    /** Habilita o deshabilita una pantalla para un rol. */
    @Transactional
    public void guardarPantalla(String rolNombre, String pantalla, boolean permitido) {
        Rol rol = rolPorNombre(rolNombre);
        AccesoPantallaRol acceso = accesoRepository
                .findByRolNombreAndPantalla(rol.getNombre(), pantalla)
                .orElseGet(() -> {
                    AccesoPantallaRol nuevo = new AccesoPantallaRol();
                    nuevo.setRol(rol);
                    nuevo.setPantalla(pantalla);
                    return nuevo;
                });
        acceso.setPermitido(permitido);
        accesoRepository.save(acceso);
    }

    /** Los roles que se pueden configurar. */
    @Transactional(readOnly = true)
    public List<String> roles() {
        return rolRepository.findAll().stream().map(Rol::getNombre).sorted().toList();
    }

    private Rol rolPorNombre(String nombre) {
        return rolRepository.findAll().stream()
                .filter(r -> r.getNombre().equalsIgnoreCase(nombre))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("seguridad.rol.no.encontrado", nombre));
    }
}
