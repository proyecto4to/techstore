package com.techstore.direcciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DireccionEntregaRepository extends JpaRepository<DireccionEntrega, Long> {

    List<DireccionEntrega> findByUsuarioIdOrderByPrincipalDescUpdatedAtDesc(Long usuarioId);

    Optional<DireccionEntrega> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByUsuarioId(Long usuarioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DireccionEntrega d set d.principal = false "
            + "where d.usuario.id = :usuarioId and d.id <> :direccionId and d.principal = true")
    void quitarPrincipalExcepto(@Param("usuarioId") Long usuarioId,
                                @Param("direccionId") Long direccionId);
}
