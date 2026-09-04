package com.techstore.seguridad;

import com.techstore.usuarios.Rol;
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

/** Qué pantallas de la aplicación ve un rol. */
@Entity
@Table(name = "acceso_pantalla_rol")
@Getter
@Setter
@NoArgsConstructor
public class AccesoPantallaRol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    /**
     * El nombre de la pantalla tal como la conoce la app.
     *
     * <p>Es texto y no un enum a propósito: la lista de pantallas la define el
     * cliente móvil y cambia más seguido que el dominio. Un nombre que el
     * backend no conozca no rompe nada, simplemente no lo usa nadie.
     */
    @Column(nullable = false, length = 60)
    private String pantalla;

    @Column(nullable = false)
    private Boolean permitido = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
