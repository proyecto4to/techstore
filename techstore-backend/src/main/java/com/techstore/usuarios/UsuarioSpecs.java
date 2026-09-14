package com.techstore.usuarios;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Filtros de la lista de usuarios.
 *
 * <p>Va por Specification, igual que la auditoría, y no por una consulta con
 * {@code :param IS NULL OR ...}: con parámetros nulos PostgreSQL no puede
 * deducir el tipo y la consulta falla en ejecución. Acá el predicado
 * directamente no se agrega.
 *
 * <p>El filtrado se hace en la base y no en memoria porque la pantalla de
 * usuarios está paginada: filtrar después de traer la página devolvería
 * páginas a medio llenar y resultados que aparecen y desaparecen al navegar.
 */
public final class UsuarioSpecs {

    private UsuarioSpecs() {
    }

    public static Specification<Usuario> conFiltros(String texto, String rol, Boolean activo) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
                predicados.add(cb.or(
                        cb.like(cb.lower(root.get("email")), patron),
                        cb.like(cb.lower(root.get("nombre")), patron),
                        cb.like(cb.lower(root.get("apellido")), patron),
                        // Quien busca «Ana Gómez» escribe el nombre completo, que
                        // en la base está partido en dos columnas.
                        cb.like(cb.lower(cb.concat(cb.concat(root.get("nombre"), " "),
                                root.get("apellido"))), patron)));
            }
            if (rol != null && !rol.isBlank()) {
                predicados.add(cb.equal(root.get("rol").get("nombre"), rol.trim()));
            }
            if (activo != null) {
                predicados.add(cb.equal(root.get("activo"), activo));
            }
            return predicados.isEmpty() ? cb.conjunction() : cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
