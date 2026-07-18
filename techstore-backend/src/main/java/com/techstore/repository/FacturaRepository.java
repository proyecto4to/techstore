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
import java.util.Optional;

public interface FacturaRepository extends JpaRepository<Factura, Long> {

    @Query("select coalesce(sum(f.total), 0) from Factura f where f.estado = :estado")
    BigDecimal totalPorEstado(@Param("estado") EstadoFactura estado);

    long countByEstado(EstadoFactura estado);

    @Override
    @EntityGraph(attributePaths = {"pedido"})
    Page<Factura> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"pedido"})
    Optional<Factura> findWithPedidoById(Long id);

    @EntityGraph(attributePaths = {"pedido", "pedido.detalles", "pedido.detalles.producto", "pedido.usuario", "usuario"})
    Optional<Factura> findParaPdfById(Long id);
}
