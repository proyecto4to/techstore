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
            "conversacion.cliente", "conversacion.pedido"})
    Optional<Mensaje> findForNotificationById(Long id);

    long countByRemitenteIdAndEnviadoEnAfter(Long remitenteId, LocalDateTime desde);

    @Query("select count(m) from Mensaje m where m.conversacion.id = :conversacionId "
            + "and m.leidoEn is null and m.remitente.id <> :clienteId")
    long contarNoLeidosCliente(@Param("conversacionId") Long conversacionId,
                               @Param("clienteId") Long clienteId);

    @Query("select count(m) from Mensaje m where m.conversacion.id = :conversacionId "
            + "and m.leidoEn is null and m.remitente.id = :clienteId")
    long contarNoLeidosStaff(@Param("conversacionId") Long conversacionId,
                             @Param("clienteId") Long clienteId);

    @Query("select count(m) from Mensaje m where m.leidoEn is null "
            + "and m.remitente.id = m.conversacion.cliente.id")
    long contarNoLeidosStaffGlobal();

    @Query("select count(m) from Mensaje m where m.leidoEn is null "
            + "and m.conversacion.cliente.id = :clienteId and m.remitente.id <> :clienteId")
    long contarNoLeidosClienteGlobal(@Param("clienteId") Long clienteId);

    @Modifying
    @Query("update Mensaje m set m.leidoEn = :ahora, m.entregadoEn = coalesce(m.entregadoEn, :ahora) "
            + "where m.conversacion.id = :conversacionId and m.remitente.id <> :clienteId and m.leidoEn is null")
    int marcarLeidosCliente(@Param("conversacionId") Long conversacionId,
                            @Param("clienteId") Long clienteId,
                            @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Query("update Mensaje m set m.leidoEn = :ahora, m.entregadoEn = coalesce(m.entregadoEn, :ahora) "
            + "where m.conversacion.id = :conversacionId and m.remitente.id = :clienteId and m.leidoEn is null")
    int marcarLeidosStaff(@Param("conversacionId") Long conversacionId,
                          @Param("clienteId") Long clienteId,
                          @Param("ahora") LocalDateTime ahora);
}
