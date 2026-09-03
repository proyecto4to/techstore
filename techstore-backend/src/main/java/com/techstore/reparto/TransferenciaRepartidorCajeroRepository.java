package com.techstore.reparto;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransferenciaRepartidorCajeroRepository
        extends JpaRepository<TransferenciaRepartidorCajero, Long> {
    @EntityGraph(attributePaths = {"repartidor", "cajero", "cierre", "respondidoPor"})
    Page<TransferenciaRepartidorCajero> findByCajeroEmailOrderByFechaSolicitudDesc(
            String email, Pageable pageable);

    @EntityGraph(attributePaths = {"repartidor", "cajero", "cierre", "respondidoPor"})
    Page<TransferenciaRepartidorCajero> findByRepartidorEmailOrderByFechaSolicitudDesc(
            String email, Pageable pageable);

    @EntityGraph(attributePaths = {"repartidor", "cajero", "cierre", "respondidoPor"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransferenciaRepartidorCajero t where t.id = :id")
    Optional<TransferenciaRepartidorCajero> findForUpdateById(@Param("id") Long id);

    long countByRepartidorEmailAndEstado(String email, EstadoTransferencia estado);
}
