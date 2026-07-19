package com.techstore.repository;

import com.techstore.entity.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    boolean existsByRuc(String ruc);

    List<Proveedor> findByActivoTrueOrderByRazonSocialAsc();
}
