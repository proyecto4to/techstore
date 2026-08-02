package com.techstore.usuarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

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

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    /** RUC del cliente en formato {@code NNNNNNNN-D}, si factura como contribuyente. */
    @Column(length = 20)
    private String ruc;

    /** Razón social a mostrar en la factura; si está vacía se usa nombre y apellido. */
    @Column(name = "razon_social", length = 255)
    private String razonSocial;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    /**
     * Desde qué caja o sucursal emite este usuario. Así se detecta el punto de
     * expedición al facturar: por quién factura, no por la máquina, que con
     * DHCP o una notebook que se mueve cambiaría de dirección.
     *
     * <p>Nulo significa el punto por defecto de la empresa, que es como venían
     * funcionando todos antes de que hubiera más de uno.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "punto_expedicion_id")
    private com.techstore.empresa.PuntoExpedicion puntoExpedicion;

    private Boolean activo = true;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
