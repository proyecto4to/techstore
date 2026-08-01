package com.techstore.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    boolean existsByPedidoId(Long pedidoId);
}
