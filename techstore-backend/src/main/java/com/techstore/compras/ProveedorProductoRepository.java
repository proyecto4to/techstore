package com.techstore.compras;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProveedorProductoRepository extends JpaRepository<ProveedorProducto, Long> {

    /** Todas las ofertas que cubren alguno de estos productos. */
    List<ProveedorProducto> findByProductoIdIn(Collection<Long> productoIds);

    Optional<ProveedorProducto> findByProveedorIdAndProductoId(Long proveedorId, Long productoId);

    List<ProveedorProducto> findByProveedorId(Long proveedorId);
}
