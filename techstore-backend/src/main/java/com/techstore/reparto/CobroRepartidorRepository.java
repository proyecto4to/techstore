package com.techstore.reparto;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CobroRepartidorRepository extends JpaRepository<CobroRepartidor, Long> {
    boolean existsByPedidoId(Long pedidoId);
    boolean existsByAsignacionId(Long asignacionId);
    long countByRepartidorEmailAndEstado(String email, EstadoMovimientoCaja estado);
}
