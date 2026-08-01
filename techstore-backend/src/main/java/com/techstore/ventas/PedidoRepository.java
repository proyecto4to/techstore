package com.techstore.ventas;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
