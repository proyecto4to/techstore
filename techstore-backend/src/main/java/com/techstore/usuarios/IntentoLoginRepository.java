package com.techstore.usuarios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface IntentoLoginRepository extends JpaRepository<IntentoLogin, String> {

    /** Limpieza de registros viejos, para que la tabla no crezca sin límite. */
    @Modifying
    @Query("delete from IntentoLogin i where i.ultimoIntento < :limite "
            + "and (i.bloqueadoHasta is null or i.bloqueadoHasta < :limite)")
    int borrarAnterioresA(@Param("limite") LocalDateTime limite);
}
