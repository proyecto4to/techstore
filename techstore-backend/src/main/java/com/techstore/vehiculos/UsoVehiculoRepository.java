package com.techstore.vehiculos;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsoVehiculoRepository extends JpaRepository<UsoVehiculo, Long> {
    @EntityGraph(attributePaths = {"vehiculo", "repartidor"})
    Optional<UsoVehiculo> findByRepartidorEmailAndEstado(String email, EstadoUsoVehiculo estado);
    boolean existsByRepartidorIdAndEstado(Long repartidorId, EstadoUsoVehiculo estado);
    boolean existsByVehiculoIdAndEstado(Long vehiculoId, EstadoUsoVehiculo estado);

    @EntityGraph(attributePaths = {"vehiculo", "repartidor"})
    List<UsoVehiculo> findByEstado(EstadoUsoVehiculo estado);

    @EntityGraph(attributePaths = {"vehiculo", "repartidor"})
    List<UsoVehiculo> findByVehiculoIdOrderByFechaInicioDesc(Long vehiculoId);

    @EntityGraph(attributePaths = {"vehiculo", "repartidor"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsoVehiculo u where u.repartidor.email = :email and u.estado = :estado")
    Optional<UsoVehiculo> findOwnForUpdate(@Param("email") String email,
                                           @Param("estado") EstadoUsoVehiculo estado);
}
