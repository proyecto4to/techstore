package com.techstore.repository;

import com.techstore.dto.response.TopProductoResponse;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.PedidoDetalle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PedidoDetalleRepository extends JpaRepository<PedidoDetalle, Long> {

    @Query("""
            select new com.techstore.dto.response.TopProductoResponse(
                d.producto.id, d.producto.nombre, sum(d.cantidad), sum(d.subtotal))
            from PedidoDetalle d
            where d.pedido.estado in :estados
                and d.pedido.fecha >= :desde and d.pedido.fecha < :hasta
            group by d.producto.id, d.producto.nombre
            order by sum(d.cantidad) desc
            """)
    List<TopProductoResponse> topProductos(@Param("estados") Collection<EstadoPedido> estados,
                                           @Param("desde") LocalDateTime desde,
                                           @Param("hasta") LocalDateTime hasta,
                                           Pageable pageable);
}
