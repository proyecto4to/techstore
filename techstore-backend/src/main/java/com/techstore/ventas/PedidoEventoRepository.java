package com.techstore.ventas;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoEventoRepository extends JpaRepository<PedidoEvento, Long> {

    List<PedidoEvento> findByPedidoIdOrderByCreatedAtAscIdAsc(Long pedidoId);
}
