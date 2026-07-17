package com.techstore.repository;

import com.techstore.entity.Producto;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ProductoSpecs {

    private ProductoSpecs() {
    }

    public static Specification<Producto> conFiltros(String search, Long marcaId, boolean incluirInactivos) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (!incluirInactivos) {
                predicados.add(cb.isTrue(root.get("activo")));
            }
            if (search != null && !search.isBlank()) {
                predicados.add(cb.like(cb.lower(root.get("nombre")), "%" + search.trim().toLowerCase() + "%"));
            }
            if (marcaId != null) {
                predicados.add(cb.equal(root.get("marca").get("id"), marcaId));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
