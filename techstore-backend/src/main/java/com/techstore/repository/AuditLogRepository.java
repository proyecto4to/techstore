package com.techstore.repository;

import com.techstore.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {

    /** Para poblar el filtro de la pantalla sin adivinar qué se auditó. */
    @Query("SELECT DISTINCT a.entidad FROM AuditLog a ORDER BY a.entidad")
    List<String> entidadesRegistradas();
}
