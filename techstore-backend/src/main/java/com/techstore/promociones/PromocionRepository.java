package com.techstore.promociones;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PromocionRepository extends JpaRepository<Promocion, Long> {

    /**
     * Las que corresponde aplicar ahora mismo.
     *
     * <p>Trae la marca y el producto en la misma consulta: resolver el precio
     * de una página de catálogo pregunta por el alcance de cada promoción, y
     * sin esto cada una dispararía su propia consulta.
     */
    @Query("""
            select p from Promocion p
            left join fetch p.producto
            left join fetch p.marca
            where p.activa = true and p.desde <= :momento and p.hasta >= :momento
            """)
    List<Promocion> vigentesEn(@Param("momento") LocalDateTime momento);

    @Query("""
            select p from Promocion p
            left join fetch p.producto
            left join fetch p.marca
            """)
    Page<Promocion> listar(Pageable pageable);

    boolean existsByProductoId(Long productoId);

    boolean existsByMarcaId(Long marcaId);
}
