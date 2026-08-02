package com.techstore.empresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;

/**
 * Una caja o sucursal que emite documentos. Cada una lleva su propia serie:
 * {@code 001-001-0000001} y {@code 001-002-0000001} son dos documentos
 * distintos y los dos válidos.
 */
@Entity
@Table(name = "puntos_expedicion")
@Getter
@Setter
@NoArgsConstructor
public class PuntoExpedicion {

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

    @Column(nullable = false, length = 3)
    private String establecimiento;

    @Column(nullable = false, length = 3)
    private String punto;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private Boolean activo = true;

    /**
     * El que se usa cuando quien emite no tiene uno asignado. Hay exactamente
     * uno por empresa; lo garantiza un índice único parcial en la base.
     */
    @Column(name = "por_defecto", nullable = false)
    private Boolean porDefecto = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** El prefijo con el que salen los documentos de este punto: {@code 001-002}. */
    public String prefijo() {
        return establecimiento + "-" + punto;
    }

    /**
     * Nombre del autonumerador de esta entidad para este punto.
     *
     * <p>El punto por defecto usa la entidad pelada ({@code FACTURA}) a
     * propósito: es la serie que ya venía corriendo, y cambiarle el nombre la
     * reiniciaría en 1 sobre un timbrado en uso.
     */
    public String autonumerador(String entidad) {
        return Boolean.TRUE.equals(porDefecto) ? entidad : entidad + "-" + prefijo();
    }
}
