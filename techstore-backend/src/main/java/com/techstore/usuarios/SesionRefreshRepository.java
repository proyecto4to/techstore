package com.techstore.usuarios;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SesionRefreshRepository extends JpaRepository<SesionRefresh, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SesionRefresh s join fetch s.usuario u join fetch u.rol where s.tokenId = :tokenId")
    Optional<SesionRefresh> buscarParaActualizar(@Param("tokenId") UUID tokenId);

    List<SesionRefresh> findAllByUsuarioIdAndRevocadoEnIsNull(Long usuarioId);
}
