package com.techstore.direcciones;

import com.techstore.usuarios.Usuario;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "direcciones_entrega")
@Getter
@Setter
@NoArgsConstructor
public class DireccionEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /**
     * Cómo le dice la persona a este lugar: «Casa», «Trabajo», «Depósito».
     *
     * <p>Es lo único de la dirección que el mapa no puede deducir, y es lo que
     * se lee en la lista: elegir entre dos calles parecidas es más difícil que
     * elegir entre «Casa» y «Trabajo».
     */
    @Column(length = 60)
    private String descripcion;

    @Column(name = "nombre_destinatario", nullable = false, length = 120)
    private String nombreDestinatario;

    @Column(nullable = false, length = 20)
    private String telefono;

    // Departamento, ciudad y la primera línea se completan solos desde el
    // punto marcado. Si el servicio de direcciones no contesta, la ubicación
    // igual queda guardada: la fuente principal son las coordenadas.
    @Column(length = 80)
    private String departamento;

    @Column(length = 100)
    private String ciudad;

    @Column(length = 100)
    private String barrio;

    @Column(name = "direccion_linea_1", length = 200)
    private String direccionLinea1;

    @Column(name = "direccion_linea_2", length = 200)
    private String direccionLinea2;

    @Column(name = "numero_casa", length = 40)
    private String numeroCasa;

    @Column(length = 300)
    private String referencia;

    @Column(name = "codigo_postal", length = 20)
    private String codigoPostal;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitud;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitud;

    @Column(nullable = false)
    private Boolean principal = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

