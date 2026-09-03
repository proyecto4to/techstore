package com.techstore.reparto;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AsignacionRepartoRepository extends JpaRepository<AsignacionReparto, Long> {
    @EntityGraph(attributePaths = {"pedido", "pedido.usuario", "envio", "repartidor", "asignadoPor"})
    List<AsignacionReparto> findByPedidoIdOrderByFechaAsignacionDescIdDesc(Long pedidoId);

    @EntityGraph(attributePaths = {"pedido", "pedido.usuario", "envio", "repartidor", "asignadoPor"})
    Page<AsignacionReparto> findByRepartidorEmail(String email, Pageable pageable);

    @EntityGraph(attributePaths = {"pedido", "pedido.usuario", "envio", "repartidor", "asignadoPor"})
    Page<AsignacionReparto> findByRepartidorEmailAndEstadoIn(
            String email, Collection<EstadoReparto> estados, Pageable pageable);

    @EntityGraph(attributePaths = {"pedido", "pedido.usuario", "envio", "repartidor", "asignadoPor"})
    List<AsignacionReparto> findByRepartidorEmailAndEstadoInOrderByOrdenEntregaAscFechaAsignacionAsc(
            String email, Collection<EstadoReparto> estados);

    @EntityGraph(attributePaths = {"pedido", "pedido.usuario", "envio", "repartidor", "asignadoPor"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AsignacionReparto a where a.id = :id")
    Optional<AsignacionReparto> findForUpdateById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AsignacionReparto a where a.pedido.id = :pedidoId and a.estado in :estados")
    Optional<AsignacionReparto> findActiveForUpdateByPedidoId(
            @Param("pedidoId") Long pedidoId, @Param("estados") Collection<EstadoReparto> estados);

    long countByRepartidorEmailAndEstado(String email, EstadoReparto estado);
    long countByRepartidorEmailAndEstadoIn(String email, Collection<EstadoReparto> estados);
}
