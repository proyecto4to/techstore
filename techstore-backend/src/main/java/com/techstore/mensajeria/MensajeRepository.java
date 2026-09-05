package com.techstore.mensajeria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    @EntityGraph(attributePaths = {"remitente", "remitente.rol"})
    Page<Mensaje> findByConversacionIdOrderByEnviadoEnDescIdDesc(Long conversacionId, Pageable pageable);

    @EntityGraph(attributePaths = {"remitente", "remitente.rol"})
    Optional<Mensaje> findFirstByConversacionIdOrderByEnviadoEnDescIdDesc(Long conversacionId);

    @EntityGraph(attributePaths = {"remitente", "remitente.rol", "conversacion"})
    Optional<Mensaje> findByRemitenteIdAndClientMessageId(Long remitenteId, String clientMessageId);

    @EntityGraph(attributePaths = {"remitente", "remitente.rol", "conversacion",
            "conversacion.iniciador", "conversacion.destinatario", "conversacion.pedido"})
    Optional<Mensaje> findForNotificationById(Long id);

    long countByRemitenteIdAndEnviadoEnAfter(Long remitenteId, LocalDateTime desde);

    /**
     * Lo que le falta leer a {@code usuarioId} en una conversación.
     *
     * <p>Se mide contra quien mira y no contra un rol: en una privada las dos
     * partes son simétricas, y en soporte quien atiende puede ser cualquiera
     * del mostrador.
     */
    @Query("select count(m) from Mensaje m where m.conversacion.id = :conversacionId "
            + "and m.leidoEn is null and m.remitente.id <> :usuarioId")
    long contarNoLeidosPara(@Param("conversacionId") Long conversacionId,
                            @Param("usuarioId") Long usuarioId);

    /** Lo que el mostrador todavía no leyó de una conversación de soporte. */
    @Query("select count(m) from Mensaje m where m.conversacion.id = :conversacionId "
            + "and m.leidoEn is null and m.remitente.id = :iniciadorId")
    long contarNoLeidosDelIniciador(@Param("conversacionId") Long conversacionId,
                                    @Param("iniciadorId") Long iniciadorId);

    /** Pendientes en las conversaciones donde el usuario es una de las partes. */
    @Query("select count(m) from Mensaje m join m.conversacion c left join c.destinatario d "
            + "where m.leidoEn is null and m.remitente.id <> :usuarioId "
            + "and (c.iniciador.id = :usuarioId or d.id = :usuarioId)")
    long contarNoLeidosComoParte(@Param("usuarioId") Long usuarioId);

    /** Pendientes de todo el soporte, para el badge del mostrador. */
    @Query("select count(m) from Mensaje m where m.leidoEn is null "
            + "and m.conversacion.tipo = com.techstore.mensajeria.TipoConversacion.SOPORTE "
            + "and m.remitente.id = m.conversacion.iniciador.id")
    long contarNoLeidosDeSoporte();

    @Modifying
    @Query("update Mensaje m set m.leidoEn = :ahora, m.entregadoEn = coalesce(m.entregadoEn, :ahora) "
            + "where m.conversacion.id = :conversacionId and m.remitente.id <> :usuarioId "
            + "and m.leidoEn is null")
    int marcarLeidosPara(@Param("conversacionId") Long conversacionId,
                         @Param("usuarioId") Long usuarioId,
                         @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Query("update Mensaje m set m.leidoEn = :ahora, m.entregadoEn = coalesce(m.entregadoEn, :ahora) "
            + "where m.conversacion.id = :conversacionId and m.remitente.id = :iniciadorId "
            + "and m.leidoEn is null")
    int marcarLeidosDelIniciador(@Param("conversacionId") Long conversacionId,
                                 @Param("iniciadorId") Long iniciadorId,
                                 @Param("ahora") LocalDateTime ahora);
}
