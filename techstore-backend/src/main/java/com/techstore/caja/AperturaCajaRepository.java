package com.techstore.caja;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AperturaCajaRepository extends JpaRepository<AperturaCaja, Long> {

    /** Turno en curso (el que todavía no se cerró), si hay alguno. */
    @EntityGraph(attributePaths = {"usuario"})
    Optional<AperturaCaja> findByCierreIsNull();

    /**
     * Igual que {@link #findByCierreIsNull()} pero bloqueando la fila: evita que
     * dos cierres (o dos aperturas) simultáneos se pisen.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AperturaCaja a where a.cierre is null")
    Optional<AperturaCaja> findAbiertaForUpdate();
}
