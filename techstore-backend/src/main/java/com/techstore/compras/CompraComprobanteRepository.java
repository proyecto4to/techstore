package com.techstore.compras;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompraComprobanteRepository extends JpaRepository<CompraComprobante, Long> {

    @EntityGraph(attributePaths = {"pedido"})
    Optional<CompraComprobante> findByPedidoId(Long pedidoId);

    /**
     * Para no acreditar dos veces la misma factura del proveedor: el par
     * timbrado + número identifica al comprobante en todo el país.
     */
    @EntityGraph(attributePaths = {"pedido"})
    Optional<CompraComprobante> findByTimbradoAndNumero(String timbrado, String numero);

    /**
     * Los comprobantes de un período, ordenados por fecha y número: es el orden
     * cronológico que exige el libro de compras.
     *
     * <p>El rango va por {@code fecha_emision} —la del papel— y no por la fecha
     * en que se cargó: el crédito fiscal corresponde al período del comprobante.
     */
    @EntityGraph(attributePaths = {"pedido"})
    @Query("select c from CompraComprobante c "
            + "where c.fechaEmision >= :desde and c.fechaEmision <= :hasta "
            + "order by c.fechaEmision, c.numero")
    List<CompraComprobante> delPeriodo(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
