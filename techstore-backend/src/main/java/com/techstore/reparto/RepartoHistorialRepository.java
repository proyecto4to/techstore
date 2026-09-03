package com.techstore.reparto;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepartoHistorialRepository extends JpaRepository<RepartoHistorial, Long> {
    @EntityGraph(attributePaths = "usuario")
    List<RepartoHistorial> findByAsignacionIdOrderByFechaAscIdAsc(Long asignacionId);
}
