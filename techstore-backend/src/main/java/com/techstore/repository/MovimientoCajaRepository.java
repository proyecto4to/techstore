package com.techstore.repository;

import com.techstore.entity.CierreCaja;
import com.techstore.entity.MovimientoCaja;
import com.techstore.entity.TipoMovimiento;
import com.techstore.entity.TipoPago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Total pendiente de arqueo (todavía sin cierre asignado), por tipo y medio. */
    @Query("select coalesce(sum(m.monto), 0) from MovimientoCaja m "
            + "where m.tipo = :tipo and m.medio = :medio and m.cierre is null")
    BigDecimal totalPendientePorTipoYMedio(@Param("tipo") TipoMovimiento tipo,
                                           @Param("medio") TipoPago medio);

    /** Total ya arqueado por un cierre puntual, por tipo y medio. */
    @Query("select coalesce(sum(m.monto), 0) from MovimientoCaja m "
            + "where m.tipo = :tipo and m.medio = :medio and m.cierre.id = :cierreId")
    BigDecimal totalDeCierrePorTipoYMedio(@Param("tipo") TipoMovimiento tipo,
                                          @Param("medio") TipoPago medio,
                                          @Param("cierreId") Long cierreId);

    /**
     * Asigna al cierre todos los movimientos pendientes y devuelve cuántos tomó.
     * Se hace antes de sumar, así lo sumado es exactamente lo reclamado por más
     * que entren movimientos nuevos en el medio.
     */
    @Modifying
    @Query("update MovimientoCaja m set m.cierre = :cierre where m.cierre is null")
    int asignarPendientesA(@Param("cierre") CierreCaja cierre);
}
