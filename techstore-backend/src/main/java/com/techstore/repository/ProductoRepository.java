package com.techstore.repository;

import com.techstore.entity.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    /**
     * Bloquea las filas de productos durante la transacción para evitar
     * sobreventa cuando dos checkouts compiten por el mismo stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id in :ids")
    List<Producto> findAllForUpdate(@Param("ids") Collection<Long> ids);

    @Query("select p from Producto p where p.activo = true "
            + "and (p.stockReal - p.stockComprometido) <= :umbral "
            + "order by (p.stockReal - p.stockComprometido) asc, p.nombre asc")
    List<Producto> conStockBajo(@Param("umbral") int umbral, Pageable pageable);
}
