package com.techstore.ventas;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagoComprobanteRepository extends JpaRepository<PagoComprobante, Long> {

    List<PagoComprobante> findByPedidoIdOrderByCreatedAtAsc(Long pedidoId);

    long countByPedidoId(Long pedidoId);

    Optional<PagoComprobante> findByIdAndPedidoId(Long id, Long pedidoId);
}
