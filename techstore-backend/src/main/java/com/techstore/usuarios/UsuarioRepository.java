package com.techstore.usuarios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    @EntityGraph(attributePaths = "rol")
    Optional<Usuario> findWithRolById(Long id);

    /** Serializa checkouts idempotentes del mismo usuario. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.email = :email")
    Optional<Usuario> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u join fetch u.rol where u.id = :id")
    Optional<Usuario> findByIdForUpdate(@Param("id") Long id);

    boolean existsByEmail(String email);

    List<Usuario> findByRolNombreInAndActivoTrue(Collection<String> roles);

    /**
     * Usuarios activos de ciertos roles, buscados por nombre.
     *
     * <p>El patrón llega armado y en minúsculas —{@code %ana%}, o {@code %} para
     * traer todo— en lugar de un texto que pueda venir nulo. Con un parámetro
     * nulo, PostgreSQL no puede inferir el tipo dentro de {@code lower(...)} y
     * la consulta falla con «no existe la función lower(bytea)».
     *
     * <p>Se compara contra el nombre, el apellido y el nombre completo armado,
     * para que «Ana Gómez» encuentre a quien cargó el apellido por separado.
     */
    @Query("select u from Usuario u join fetch u.rol r "
            + "where u.activo = true and r.nombre in :roles and u.id <> :excluidoId "
            + "and (lower(u.nombre) like :patron "
            + "     or lower(u.apellido) like :patron "
            + "     or lower(concat(u.nombre, ' ', u.apellido)) like :patron) "
            + "order by u.nombre asc, u.apellido asc")
    List<Usuario> buscarParaConversar(@Param("roles") Collection<String> roles,
                                      @Param("patron") String patron,
                                      @Param("excluidoId") Long excluidoId);
}
