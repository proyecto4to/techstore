package com.techstore.facturacion;

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

    @EntityGraph(attributePaths = {"pedido", "pedido.usuario"})
    Page<Factura> findByPedidoUsuarioEmail(String email, Pageable pageable);

    @EntityGraph(attributePaths = {"pedido", "pedido.usuario"})
    Optional<Factura> findWithPedidoById(Long id);

    @EntityGraph(attributePaths = {"pedido", "pedido.detalles", "pedido.detalles.producto", "pedido.usuario", "usuario"})
    Optional<Factura> findParaPdfById(Long id);

    @EntityGraph(attributePaths = {"pedido", "pedido.detalles", "pedido.detalles.producto", "pedido.usuario", "usuario"})
    Optional<Factura> findParaPdfByPedidoId(Long pedidoId);

    Optional<Factura> findByPedidoId(Long pedidoId);

    /**
     * Las facturas de un período en orden cronológico, para el libro de IVA de
     * ventas. Trae los detalles porque el desglose por tasa sale de ahí.
     *
     * <p>Las anuladas quedan afuera: un documento anulado no se declara. Una
     * devolución, incluso total, no cambia la factura a este estado: se declara
     * la factura original y también la nota de crédito que la compensa.
     */
    default List<Factura> delPeriodo(LocalDateTime desde, LocalDateTime hasta) {
        return delPeriodoExcepto(desde, hasta, EstadoFactura.Anulada);
    }

    /**
     * El estado a excluir viaja como parámetro y no como literal dentro del
     * JPQL: {@code estado_factura} es un enum nativo de PostgreSQL, y un literal
     * inline se traduce a {@code 'Anulada'::EstadoFactura} —el nombre de la clase
     * Java— que en la base no existe. Ligado como parámetro, el driver lo manda
     * con el tipo correcto.
     */
    @EntityGraph(attributePaths = {"pedido", "pedido.detalles"})
    @Query("select f from Factura f "
            + "where f.fecha >= :desde and f.fecha < :hasta and f.estado <> :excluido "
            + "order by f.fecha, f.numero")
    List<Factura> delPeriodoExcepto(@Param("desde") LocalDateTime desde,
                                    @Param("hasta") LocalDateTime hasta,
                                    @Param("excluido") EstadoFactura excluido);
}
