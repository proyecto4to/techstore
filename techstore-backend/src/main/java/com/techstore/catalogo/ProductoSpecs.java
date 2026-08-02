package com.techstore.catalogo;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ProductoSpecs {

    private ProductoSpecs() {
    }

    /**
     * @param incluirInactivos lo pide el panel de administración, que necesita
     *                         ver todo el catálogo. En falso queda la vista de
     *                         la tienda: solo lo que un cliente puede comprar.
     */
    public static Specification<Producto> conFiltros(String search, Long marcaId, boolean incluirInactivos) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (!incluirInactivos) {
                predicados.add(cb.isTrue(root.get("activo")));
                // Sin stock no se muestra: ofrecer algo que no se puede entregar
                // hace perder la venta dos veces, la del producto y la confianza.
                // Se mide contra el disponible (real − comprometido), porque lo
                // reservado por pedidos pendientes ya está vendido.
                predicados.add(cb.or(
                        cb.isFalse(root.get("ocultarSinStock")),
                        cb.greaterThan(
                                cb.diff(
                                        cb.coalesce(root.get("stockReal"), 0),
                                        cb.coalesce(root.get("stockComprometido"), 0)),
                                0)));
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
