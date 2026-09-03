package com.techstore.reparto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface MovimientoCajaRepartidorRepository extends JpaRepository<MovimientoCajaRepartidor, Long> {
    @Query("select coalesce(sum(case when m.naturaleza = :credito then m.monto else -m.monto end), 0) "
            + "from MovimientoCajaRepartidor m where m.repartidor.id = :repartidorId and m.estado = :estado")
    BigDecimal saldo(@Param("repartidorId") Long repartidorId,
                     @Param("credito") NaturalezaMovimiento credito,
                     @Param("estado") EstadoMovimientoCaja estado);

    @EntityGraph(attributePaths = {"repartidor", "usuario"})
    Page<MovimientoCajaRepartidor> findByRepartidorEmailOrderByFechaDescIdDesc(String email, Pageable pageable);
}
