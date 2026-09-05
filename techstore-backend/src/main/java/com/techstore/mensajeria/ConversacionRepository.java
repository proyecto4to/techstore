package com.techstore.mensajeria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Las consultas van con {@code left join c.destinatario} explícito y no con
 * {@code c.destinatario.id}: en las conversaciones de soporte el destinatario es
 * nulo, y el join implícito de JPQL es interno, así que esas filas se caerían
 * del resultado aunque la condición fuera cierta por el otro lado del OR.
 */
public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {

    @EntityGraph(attributePaths = {"iniciador", "iniciador.rol", "destinatario", "destinatario.rol", "pedido"})
    @Query("select c from Conversacion c where c.id = :id")
    Optional<Conversacion> findParaAccesoById(@Param("id") Long id);

    /** Lo que ve alguien que no es del mostrador: solo donde es parte. */
    @EntityGraph(attributePaths = {"iniciador", "iniciador.rol", "destinatario", "destinatario.rol", "pedido"})
    @Query("select c from Conversacion c left join c.destinatario d "
            + "where c.iniciador.id = :usuarioId or d.id = :usuarioId")
    Page<Conversacion> findDondeParticipa(@Param("usuarioId") Long usuarioId, Pageable pageable);

    /**
     * Lo que ve el mostrador: todo el soporte, más sus propias privadas.
     *
     * <p>Las privadas ajenas quedan afuera incluso para un administrador. Es el
     * punto de que sean privadas: si administración leyera la de un cajero con
     * otra persona, no lo serían.
     */
    @EntityGraph(attributePaths = {"iniciador", "iniciador.rol", "destinatario", "destinatario.rol", "pedido"})
    @Query("select c from Conversacion c left join c.destinatario d "
            + "where c.tipo = com.techstore.mensajeria.TipoConversacion.SOPORTE "
            + "or c.iniciador.id = :usuarioId or d.id = :usuarioId")
    Page<Conversacion> findParaMostrador(@Param("usuarioId") Long usuarioId, Pageable pageable);

    @Query("select c from Conversacion c where c.tipo = com.techstore.mensajeria.TipoConversacion.SOPORTE "
            + "and c.iniciador.id = :iniciadorId and c.estado = :estado "
            + "and ((:pedidoId is null and c.pedido is null) or c.pedido.id = :pedidoId)")
    Optional<Conversacion> findSoporteAbierto(@Param("iniciadorId") Long iniciadorId,
                                              @Param("pedidoId") Long pedidoId,
                                              @Param("estado") EstadoConversacion estado);

    /** La privada entre dos personas, sin importar quién la abrió. */
    @EntityGraph(attributePaths = {"iniciador", "iniciador.rol", "destinatario", "destinatario.rol", "pedido"})
    @Query("select c from Conversacion c join c.destinatario d "
            + "where c.tipo = com.techstore.mensajeria.TipoConversacion.PRIVADA "
            + "and ((c.iniciador.id = :unoId and d.id = :otroId) "
            + "  or (c.iniciador.id = :otroId and d.id = :unoId))")
    Optional<Conversacion> findPrivadaEntre(@Param("unoId") Long unoId, @Param("otroId") Long otroId);
}
