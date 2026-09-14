package com.techstore.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermisoUsuarioRepository extends JpaRepository<PermisoUsuario, Long> {

    /**
     * La excepción que aplica a este pedido, buscada por email.
     *
     * <p>Va por email y no por id porque es lo único que trae la sesión: pedir
     * primero el usuario para después pedir su permiso serían dos consultas en
     * cada llamada a la API en vez de una.
     */
    Optional<PermisoUsuario> findByUsuarioEmailAndModuloAndAccion(
            String email, Modulo modulo, AccionPermiso accion);

    List<PermisoUsuario> findByUsuarioIdOrderByModuloAscAccionAsc(Long usuarioId);

    Optional<PermisoUsuario> findByUsuarioIdAndModuloAndAccion(
            Long usuarioId, Modulo modulo, AccionPermiso accion);

    /** Cuántas personas tienen tocada esta acción, para avisar antes de romper algo. */
    long countByModuloAndAccionAndPermitidoFalse(Modulo modulo, AccionPermiso accion);
}
