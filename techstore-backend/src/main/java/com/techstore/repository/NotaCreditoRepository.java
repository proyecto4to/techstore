package com.techstore.repository;

import com.techstore.entity.NotaCredito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    boolean existsByFacturaId(Long facturaId);

    @Override
    @EntityGraph(attributePaths = {"factura", "pedido", "usuario"})
    Page<NotaCredito> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {
            "factura", "pedido", "pedido.detalles", "pedido.detalles.producto", "pedido.usuario", "usuario"})
    Optional<NotaCredito> findParaPdfById(Long id);
}
