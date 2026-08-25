package com.techstore.usuarios;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    boolean existsByUsuarioIdAndUsadoEnIsNullAndExpiraEnAfter(Long usuarioId, LocalDateTime ahora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t join fetch t.usuario u join fetch u.rol where t.tokenHash = :hash")
    Optional<PasswordResetToken> buscarParaActualizar(@Param("hash") String hash);
}
