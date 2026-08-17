package com.techstore.notificaciones;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DispositivoMovilRepository extends JpaRepository<DispositivoMovil, Long> {

    Optional<DispositivoMovil> findByDeviceIdentifier(String deviceIdentifier);

    Optional<DispositivoMovil> findByDeviceIdentifierAndUsuarioId(String deviceIdentifier, Long usuarioId);

    List<DispositivoMovil> findByUsuarioIdAndActivoTrueOrderByUltimoUsoDesc(Long usuarioId);
}
