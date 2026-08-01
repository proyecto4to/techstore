package com.techstore.notificaciones;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CorreoRepository extends JpaRepository<Correo, Long> {
}
