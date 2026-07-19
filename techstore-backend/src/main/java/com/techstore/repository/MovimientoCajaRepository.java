package com.techstore.repository;

import com.techstore.entity.MovimientoCaja;
import com.techstore.entity.TipoMovimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    @EntityGraph(attributePaths = {"usuario"})
    Page<MovimientoCaja> findByFechaGreaterThanEqualAndFechaLessThan(
            LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    @Query("select coalesce(sum(m.monto), 0) from MovimientoCaja m "
            + "where m.tipo = :tipo and m.fecha >= :desde and m.fecha < :hasta")
    BigDecimal totalPorTipoEntre(@Param("tipo") TipoMovimiento tipo,
                                 @Param("desde") LocalDateTime desde,
                                 @Param("hasta") LocalDateTime hasta);
}
