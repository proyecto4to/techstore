package com.techstore.repository;

import com.techstore.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtros de la auditoría. Va por Specification y no por una consulta con
 * {@code :param IS NULL OR ...}: con parámetros nulos, PostgreSQL no puede
 * deducir el tipo y la consulta falla en tiempo de ejecución. Acá el predicado
 * directamente no se agrega.
 */
public final class AuditLogSpecs {

    private AuditLogSpecs() {
    }

    public static Specification<AuditLog> conFiltros(
            String entidad, Long usuarioId, LocalDateTime desde, LocalDateTime hasta) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (entidad != null && !entidad.isBlank()) {
                predicados.add(cb.equal(root.get("entidad"), entidad.trim()));
            }
            if (usuarioId != null) {
                predicados.add(cb.equal(root.get("usuarioId"), usuarioId));
            }
            if (desde != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde));
            }
            if (hasta != null) {
                predicados.add(cb.lessThan(root.get("fecha"), hasta));
            }
            return predicados.isEmpty() ? cb.conjunction() : cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
