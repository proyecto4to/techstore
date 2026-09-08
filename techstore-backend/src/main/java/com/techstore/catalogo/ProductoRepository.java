package com.techstore.catalogo;

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
     *
     * <p>El {@code order by} no es para ordenar el resultado: fija el orden en
     * que se toman los candados. Sin él, dos ventas simultáneas de los mismos
     * dos productos pueden agarrarlos en orden cruzado y quedarse esperándose
     * la una a la otra; Postgres corta el abrazo abortando a una, y quien
     * compraba ve un error de base de datos en lugar de su compra. Tomándolos
     * siempre por id ascendente, la segunda simplemente espera su turno.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id in :ids order by p.id")
    List<Producto> findAllForUpdate(@Param("ids") Collection<Long> ids);

    @Query("select p from Producto p where p.activo = true "
            + "and (p.stockReal - p.stockComprometido) <= :umbral "
            + "order by (p.stockReal - p.stockComprometido) asc, p.nombre asc")
    List<Producto> conStockBajo(@Param("umbral") int umbral, Pageable pageable);

    /**
     * Los que llegaron a su punto de reposición: el propio si lo tienen
     * definido, y si no el umbral general que recibe por parámetro. Ordena por
     * lo más urgente primero, así los agotados encabezan la lista.
     */
    @Query("select p from Producto p where p.activo = true and "
            + "((p.stockMinimo > 0 and (p.stockReal - p.stockComprometido) <= p.stockMinimo) "
            + " or (p.stockMinimo = 0 and (p.stockReal - p.stockComprometido) <= :umbralGeneral)) "
            + "order by (p.stockReal - p.stockComprometido) asc, p.nombre asc")
    List<Producto> necesitanReposicion(@Param("umbralGeneral") int umbralGeneral);

    @Query("select count(p) from Producto p where p.activo = true and "
            + "((p.stockMinimo > 0 and (p.stockReal - p.stockComprometido) <= p.stockMinimo) "
            + " or (p.stockMinimo = 0 and (p.stockReal - p.stockComprometido) <= :umbralGeneral))")
    long contarNecesitanReposicion(@Param("umbralGeneral") int umbralGeneral);
}
