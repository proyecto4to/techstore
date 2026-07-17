package com.techstore.repository;

import com.techstore.entity.Marca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarcaRepository extends JpaRepository<Marca, Long> {

    List<Marca> findByActivoTrueOrderByNombre();
}
