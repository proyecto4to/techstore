package com.techstore.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Una acción sensible que alguien hizo en el sistema: quién, qué, cuándo y con
 * qué valores. Solo se inserta; nunca se actualiza ni se borra, porque un
 * registro que se puede editar no sirve para lo único que sirve una auditoría.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Empresa duena de la fila. Hibernate lo completa al insertar y lo agrega al
     * WHERE de cada consulta, asi el aislamiento no depende de que nadie se
     * olvide de filtrar.
     */
    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String entidad;

    /** Fila afectada. Nulo cuando la acción no apunta a una sola (guardar la configuración). */
    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(nullable = false, length = 20)
    private String accion;

    /**
     * Quién lo hizo. Se guarda el id suelto, sin relación: si algún día se borra
     * el usuario, el rastro de lo que hizo tiene que sobrevivirlo.
     */
    @Column(name = "usuario_id")
    private Long usuarioId;

    /** Copia del email al momento del hecho, por el mismo motivo. */
    @Column(name = "usuario_email", length = 255)
    private String usuarioEmail;

    /** Resumen legible: "Precio: 1.500.000 -> 1.400.000". */
    @Column(length = 500)
    private String detalle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> cambios;

    private LocalDateTime fecha = LocalDateTime.now();
}
