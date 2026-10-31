package com.techstore.ventas;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("select p.estado, count(p) from Pedido p "
            + "where p.tipo = :tipo and p.fecha >= :desde and p.fecha < :hasta group by p.estado")
    List<Object[]> contarPorEstadoEntre(@Param("tipo") TipoPedido tipo,
                                        @Param("desde") LocalDateTime desde,
                                        @Param("hasta") LocalDateTime hasta);

    @Query("select p.usuario.email, concat(p.usuario.nombre, ' ', p.usuario.apellido), "
            + "count(p), coalesce(sum(p.total), 0) from Pedido p "
            + "where p.tipo = :tipo and p.estado in :estados "
            + "and p.fecha >= :desde and p.fecha < :hasta "
            + "group by p.usuario.email, p.usuario.nombre, p.usuario.apellido "
            + "order by coalesce(sum(p.total), 0) desc")
    List<Object[]> topClientes(@Param("tipo") TipoPedido tipo,
                               @Param("estados") Collection<EstadoPedido> estados,
                               @Param("desde") LocalDateTime desde,
                               @Param("hasta") LocalDateTime hasta,
                               Pageable pageable);

    Page<Pedido> findByUsuarioEmail(String email, Pageable pageable);

    @EntityGraph(attributePaths = {"proveedor", "usuario"})
    Page<Pedido> findByTipo(TipoPedido tipo, Pageable pageable);

    @EntityGraph(attributePaths = {"detalles", "detalles.producto", "usuario"})
    Optional<Pedido> findWithDetallesById(Long id);

    @EntityGraph(attributePaths = {"detalles", "detalles.producto", "usuario", "usuario.rol"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> findWithDetallesForUpdateById(@Param("id") Long id);

    /** Los pagos por revisar, del más viejo al más nuevo: el que más espera va primero. */
    @EntityGraph(attributePaths = {"usuario"})
    List<Pedido> findByTipoAndEstadoPagoInOrderByFechaAsc(TipoPedido tipo, Collection<EstadoPago> estados);

    long countByTipoAndEstadoPago(TipoPedido tipo, EstadoPago estado);

    @EntityGraph(attributePaths = {"detalles", "detalles.producto", "usuario"})
    @Query("select p from Pedido p where p.usuario.id = :usuarioId and p.idempotencyKey = :clave")
    Optional<Pedido> findWithDetallesByUsuarioIdAndIdempotencyKey(
            @Param("usuarioId") Long usuarioId,
            @Param("clave") String clave);

    /**
     * El historial de cada proveedor resumido en una fila: cuánto tarda en
     * entregar y cuántas compras terminaron recibidas o anuladas. De acá salen
     * la "rapidez" y la "fiabilidad" del comparador, calculadas en el momento
     * en vez de guardadas, porque un número guardado envejece.
     *
     * <p>SQL nativo por la resta de timestamps, que HQL no sabe hacer. No pasa
     * por el filtro de empresa de Hibernate, pero {@code pedidos} tiene Row
     * Level Security: igual solo ve lo de la empresa en curso.
     *
     * @return filas {@code [proveedorId, díasPromedioEntrega|null, recibidas, anuladas]}
     */
    @Query(value = """
            SELECT p.proveedor_id,
                   AVG(EXTRACT(EPOCH FROM (p.fecha_recepcion - p.fecha)) / 86400.0)
                       FILTER (WHERE p.fecha_recepcion IS NOT NULL),
                   COUNT(*) FILTER (WHERE p.estado = 'C'),
                   COUNT(*) FILTER (WHERE p.estado = 'X')
            FROM pedidos p
            WHERE p.tipo = 'Compra' AND p.proveedor_id IS NOT NULL
            GROUP BY p.proveedor_id
            """, nativeQuery = true)
    List<Object[]> historialPorProveedor();
}
