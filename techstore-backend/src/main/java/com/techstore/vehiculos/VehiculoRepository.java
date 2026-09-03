package com.techstore.vehiculos;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {
    boolean existsByMatriculaIgnoreCase(String matricula);
    List<Vehiculo> findAllByOrderByMatriculaAsc();
    List<Vehiculo> findByActivoTrueOrderByMatriculaAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehiculo v where v.id = :id")
    Optional<Vehiculo> findForUpdateById(@Param("id") Long id);
}
