package com.techstore.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotaCreditoDetalleRepository extends JpaRepository<NotaCreditoDetalle, Long> {

    /**
     * Cantidad ya devuelta por producto en un pedido (sumando notas de
     * crédito previas), para no permitir devolver más de lo comprado.
     */
    @Query("select d.producto.id, coalesce(sum(d.cantidad), 0) from NotaCreditoDetalle d "
            + "where d.notaCredito.pedido.id = :pedidoId group by d.producto.id")
    List<Object[]> cantidadesDevueltasPorProducto(@Param("pedidoId") Long pedidoId);
}
