package com.techstore.repository;

import com.techstore.entity.Pedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("select p.estado, count(p) from Pedido p group by p.estado")
    List<Object[]> contarPorEstado();

    Page<Pedido> findByUsuarioEmail(String email, Pageable pageable);

    @EntityGraph(attributePaths = {"detalles", "detalles.producto", "usuario"})
    Optional<Pedido> findWithDetallesById(Long id);
}
