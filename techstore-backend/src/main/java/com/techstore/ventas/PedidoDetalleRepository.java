package com.techstore.ventas;

import com.techstore.reportes.TopProductoResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PedidoDetalleRepository extends JpaRepository<PedidoDetalle, Long> {

    @Query("""
            select new com.techstore.reportes.TopProductoResponse(
                d.producto.id, d.producto.nombre, sum(d.cantidad), sum(d.subtotal))
            from PedidoDetalle d
            where d.pedido.tipo = :tipo and d.pedido.estado in :estados
                and d.pedido.fecha >= :desde and d.pedido.fecha < :hasta
            group by d.producto.id, d.producto.nombre
            order by sum(d.cantidad) desc
            """)
    List<TopProductoResponse> topProductos(@Param("tipo") TipoPedido tipo,
                                           @Param("estados") Collection<EstadoPedido> estados,
                                           @Param("desde") LocalDateTime desde,
                                           @Param("hasta") LocalDateTime hasta,
                                           Pageable pageable);

    /**
     * Unidades vendidas por producto desde una fecha, para estimar cuánto se
     * vende por día.
     *
     * <p>Cuenta las ventas Pagadas y las Devueltas: una devolución significa
     * que la venta ocurrió y hubo que reponer la unidad igual. Las anuladas no,
     * porque esas nunca salieron del depósito.
     *
     * @return filas {@code [productoId, unidades]}
     */
    @Query("""
            select d.producto.id, sum(d.cantidad)
            from PedidoDetalle d
            where d.pedido.tipo = :tipo and d.pedido.estado in :estados
                and d.pedido.fecha >= :desde
            group by d.producto.id
            """)
    List<Object[]> unidadesVendidasDesde(@Param("tipo") TipoPedido tipo,
                                         @Param("estados") Collection<EstadoPedido> estados,
                                         @Param("desde") LocalDateTime desde);

    /**
     * Días promedio entre pedir una compra y recibirla, por producto. Es lo que
     * tarda en llegar la reposición.
     *
     * <p>Solo compras ya recibidas: las pendientes todavía no dicen cuánto van
     * a tardar.
     *
     * <p>Va en SQL nativo porque HQL no sabe restar dos timestamps. Al no pasar
     * por Hibernate no lleva el filtro de empresa automático, pero la base tiene
     * Row Level Security sobre {@code pedidos}, así que igual solo devuelve
     * filas de la empresa en curso.
     *
     * @return filas {@code [productoId, díasPromedio]}
     */
    @Query(value = """
            SELECT pd.producto_id,
                   AVG(EXTRACT(EPOCH FROM (p.fecha_recepcion - p.fecha)) / 86400.0)
            FROM pedido_detalles pd
            JOIN pedidos p ON p.id = pd.pedido_id
            WHERE p.tipo = 'Compra' AND p.fecha_recepcion IS NOT NULL
            GROUP BY pd.producto_id
            """, nativeQuery = true)
    List<Object[]> diasDeEntregaPorProducto();
}
