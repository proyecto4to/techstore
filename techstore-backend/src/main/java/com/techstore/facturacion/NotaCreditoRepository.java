package com.techstore.facturacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    @Override
    @EntityGraph(attributePaths = {"factura", "pedido", "usuario", "detalles", "detalles.producto"})
    Page<NotaCredito> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {
            "factura", "pedido", "pedido.usuario", "usuario", "detalles", "detalles.producto"})
    Optional<NotaCredito> findParaPdfById(Long id);

    /**
     * Las notas de crédito de un período, para el libro de IVA de ventas: cada
     * una resta débito fiscal y se declara como un comprobante propio, con el
     * número y el timbrado de la factura que corrige.
     */
    @EntityGraph(attributePaths = {"factura", "detalles"})
    @Query("select n from NotaCredito n "
            + "where n.fecha >= :desde and n.fecha < :hasta "
            + "order by n.fecha, n.numero")
    List<NotaCredito> delPeriodo(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
