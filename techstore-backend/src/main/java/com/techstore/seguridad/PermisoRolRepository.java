package com.techstore.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermisoRolRepository extends JpaRepository<PermisoRol, Long> {

    List<PermisoRol> findByRolNombreOrderByModuloAscAccionAsc(String rolNombre);

    Optional<PermisoRol> findByRolNombreAndModuloAndAccion(
            String rolNombre, Modulo modulo, AccionPermiso accion);
}
