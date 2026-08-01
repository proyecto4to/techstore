package com.techstore.caja;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CierreCajaRepository extends JpaRepository<CierreCaja, Long> {

    /** Último cierre realizado; su fecha marca el inicio del próximo turno. */
    Optional<CierreCaja> findTopByOrderByFechaCierreDesc();

    @Override
    @EntityGraph(attributePaths = {"usuario"})
    Page<CierreCaja> findAll(Pageable pageable);
}
