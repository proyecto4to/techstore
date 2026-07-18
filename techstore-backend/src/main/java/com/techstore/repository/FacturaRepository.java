package com.techstore.repository;

import com.techstore.entity.EstadoFactura;
import com.techstore.entity.Factura;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FacturaRepository extends JpaRepository<Factura, Long> {

    @Query("select coalesce(sum(f.total), 0) from Factura f "
            + "where f.estado = :estado and f.fecha >= :desde and f.fecha < :hasta")
    BigDecimal totalPorEstadoEntre(@Param("estado") EstadoFactura estado,
                                   @Param("desde") LocalDateTime desde,
                                   @Param("hasta") LocalDateTime hasta);

    long countByEstadoAndFechaGreaterThanEqualAndFechaLessThan(
            EstadoFactura estado, LocalDateTime desde, LocalDateTime hasta);

    @Query("select cast(f.fecha as date), coalesce(sum(f.total), 0), count(f) from Factura f "
            + "where f.estado = :estado and f.fecha >= :desde and f.fecha < :hasta "
            + "group by cast(f.fecha as date) order by cast(f.fecha as date)")
    List<Object[]> ventasPorDia(@Param("estado") EstadoFactura estado,
                                @Param("desde") LocalDateTime desde,
                                @Param("hasta") LocalDateTime hasta);

    @Override
    @EntityGraph(attributePaths = {"pedido"})
    Page<Factura> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"pedido"})
    Optional<Factura> findWithPedidoById(Long id);

    @EntityGraph(attributePaths = {"pedido", "pedido.detalles", "pedido.detalles.producto", "pedido.usuario", "usuario"})
    Optional<Factura> findParaPdfById(Long id);
}
