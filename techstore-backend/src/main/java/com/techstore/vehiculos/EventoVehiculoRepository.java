package com.techstore.vehiculos;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoVehiculoRepository extends JpaRepository<EventoVehiculo, Long> {
    @EntityGraph(attributePaths = {"usoVehiculo", "vehiculo", "repartidor"})
    List<EventoVehiculo> findByVehiculoIdOrderByFechaDescIdDesc(Long vehiculoId);
    boolean existsByVehiculoIdAndRequiereAtencionTrueAndResueltoFalse(Long vehiculoId);
}
