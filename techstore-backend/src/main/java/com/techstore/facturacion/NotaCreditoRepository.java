package com.techstore.facturacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    @Override
    @EntityGraph(attributePaths = {"factura", "pedido", "usuario", "detalles", "detalles.producto"})
    Page<NotaCredito> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {
            "factura", "pedido", "pedido.usuario", "usuario", "detalles", "detalles.producto"})
    Optional<NotaCredito> findParaPdfById(Long id);
}
