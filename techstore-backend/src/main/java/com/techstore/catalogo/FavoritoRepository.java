package com.techstore.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoritoRepository extends JpaRepository<Favorito, Long> {

    @Query("select f from Favorito f join fetch f.producto p "
            + "left join fetch p.marca left join fetch p.modelo "
            + "where f.usuario.email = :email and p.activo = true "
            + "and (p.ocultarSinStock = false or (p.stockReal - p.stockComprometido) > 0) "
            + "order by f.creadoEn desc")
    List<Favorito> listarVisibles(@Param("email") String email);

    Optional<Favorito> findByUsuarioIdAndProductoId(Long usuarioId, Long productoId);

    long deleteByUsuarioIdAndProductoId(Long usuarioId, Long productoId);
}
