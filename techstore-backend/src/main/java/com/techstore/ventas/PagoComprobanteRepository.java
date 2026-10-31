package com.techstore.ventas;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PagoComprobanteRepository extends JpaRepository<PagoComprobante, Long> {

    List<PagoComprobante> findByPedidoIdOrderByCreatedAtAsc(Long pedidoId);

    long countByPedidoId(Long pedidoId);

    long countByPedidoIdAndEstado(Long pedidoId, EstadoComprobante estado);

    Optional<PagoComprobante> findByIdAndPedidoId(Long id, Long pedidoId);

    List<PagoComprobante> findByPedidoIdAndEstado(Long pedidoId, EstadoComprobante estado);

    /** Un comprobante subido antes del checkout, todavía sin pedido. */
    @Query("select c from PagoComprobante c where c.id = :id and c.pedido is null")
    Optional<PagoComprobante> findSuelto(@Param("id") Long id);

    /**
     * Los que va a usar un checkout, bloqueados: dos compras simultáneas con el
     * mismo respaldo no pueden quedarse las dos con él.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PagoComprobante c where c.id in :ids order by c.id")
    List<PagoComprobante> findAllForUpdate(@Param("ids") Collection<Long> ids);

    /** Cuántos tiene esperando un usuario, para que no se acumulen sin fin. */
    @Query("select count(c) from PagoComprobante c where c.pedido is null and c.subidoPor.id = :usuarioId")
    long contarSueltosDe(@Param("usuarioId") Long usuarioId);

    @Query("select c from PagoComprobante c where c.pedido is null and c.createdAt < :limite")
    List<PagoComprobante> findSueltosAnterioresA(@Param("limite") LocalDateTime limite);
}
