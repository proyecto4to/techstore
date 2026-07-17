package com.techstore.repository;

import com.techstore.entity.Autonumerador;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AutonumeradorRepository extends JpaRepository<Autonumerador, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Autonumerador a where a.entidad = :entidad")
    Optional<Autonumerador> findByEntidadForUpdate(@Param("entidad") String entidad);
}
