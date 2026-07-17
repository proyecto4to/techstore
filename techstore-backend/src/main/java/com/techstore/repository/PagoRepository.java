package com.techstore.repository;

import com.techstore.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    boolean existsByPedidoId(Long pedidoId);
}
