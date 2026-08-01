package com.techstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Configuración clave/valor editable desde la app (ej.: credenciales SMTP). */
@Entity
@Table(name = "configuracion")
@Getter
@Setter
@NoArgsConstructor
public class Configuracion {

    @Id
    @Column(length = 60)
    private String clave;

    /**
     * Empresa dueña de la fila. En la base la clave primaria real es
     * (tenant_id, clave), porque dos empresas necesitan poder guardar cada una
     * su propio {@code empresa.ruc}. Del lado de JPA alcanza con {@code @TenantId}
     * sobre una clave simple: Hibernate agrega el discriminador a toda consulta,
     * así que dentro de una empresa la clave vuelve a ser única.
     */
    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(columnDefinition = "text")
    private String valor;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Configuracion(String clave, String valor) {
        this.clave = clave;
        this.valor = valor;
    }
}
