package com.techstore.empresa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PuntoExpedicionRepository extends JpaRepository<PuntoExpedicion, Long> {

    Optional<PuntoExpedicion> findByPorDefectoTrue();

    Optional<PuntoExpedicion> findByEstablecimientoAndPunto(String establecimiento, String punto);
}
