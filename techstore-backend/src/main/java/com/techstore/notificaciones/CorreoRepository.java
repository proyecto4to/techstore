package com.techstore.notificaciones;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface CorreoRepository extends JpaRepository<Correo, Long> {
    boolean existsByTipoAndReferenciaAndEstadoIn(
            TipoCorreo tipo, String referencia, Collection<EstadoCorreo> estados);
}
