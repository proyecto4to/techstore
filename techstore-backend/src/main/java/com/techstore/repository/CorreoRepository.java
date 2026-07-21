package com.techstore.repository;

import com.techstore.entity.Correo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorreoRepository extends JpaRepository<Correo, Long> {
}
