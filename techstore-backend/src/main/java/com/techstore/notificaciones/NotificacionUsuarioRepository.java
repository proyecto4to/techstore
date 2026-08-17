package com.techstore.notificaciones;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificacionUsuarioRepository extends JpaRepository<NotificacionUsuario, Long> {
    Page<NotificacionUsuario> findByUsuarioId(Long usuarioId, Pageable pageable);
    Optional<NotificacionUsuario> findByIdAndUsuarioId(Long id, Long usuarioId);
    long countByUsuarioIdAndLeidaFalse(Long usuarioId);
    boolean existsBySourceOutboxId(Long sourceOutboxId);

    @Modifying
    @Query("update NotificacionUsuario n set n.leida = true, n.leidaEn = :ahora "
            + "where n.usuario.id = :usuarioId and n.leida = false")
    int marcarTodasLeidas(@Param("usuarioId") Long usuarioId, @Param("ahora") LocalDateTime ahora);
}
