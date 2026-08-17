package com.techstore.mensajeria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {
    @EntityGraph(attributePaths = {"cliente", "cliente.rol", "pedido"})
    @Query("select c from Conversacion c where c.id = :id")
    Optional<Conversacion> findParaAccesoById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"cliente", "cliente.rol", "pedido"})
    Page<Conversacion> findByClienteEmail(String email, Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "cliente.rol", "pedido"})
    Page<Conversacion> findAllBy(Pageable pageable);

    @Query("select c from Conversacion c where c.cliente.id = :clienteId and c.estado = :estado "
            + "and ((:pedidoId is null and c.pedido is null) or c.pedido.id = :pedidoId)")
    Optional<Conversacion> findAbierta(@Param("clienteId") Long clienteId,
                                       @Param("pedidoId") Long pedidoId,
                                       @Param("estado") EstadoConversacion estado);
}
