package com.techstore.envios;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnvioRepository extends JpaRepository<Envio, Long> {
    @EntityGraph(attributePaths = {"pedido", "pedido.usuario", "eventos", "tarifa"})
    Optional<Envio> findByPedidoId(Long pedidoId);

    @EntityGraph(attributePaths = {"pedido", "pedido.usuario", "eventos", "tarifa"})
    Optional<Envio> findWithPedidoById(Long id);
}
