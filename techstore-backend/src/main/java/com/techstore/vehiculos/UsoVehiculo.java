package com.techstore.vehiculos;

import com.techstore.usuarios.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "usos_vehiculo")
@Getter
@Setter
@NoArgsConstructor
public class UsoVehiculo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "vehiculo_id", nullable = false)
    private Vehiculo vehiculo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "repartidor_id", nullable = false)
    private Usuario repartidor;
    @Column(name = "km_inicio", nullable = false, precision = 12, scale = 1)
    private BigDecimal kmInicio;
    @Column(name = "combustible_inicio", nullable = false, precision = 5, scale = 2)
    private BigDecimal combustibleInicio;
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio = LocalDateTime.now();
    @Column(name = "km_fin", precision = 12, scale = 1)
    private BigDecimal kmFin;
    @Column(name = "combustible_fin", precision = 5, scale = 2)
    private BigDecimal combustibleFin;
    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EstadoUsoVehiculo estado = EstadoUsoVehiculo.ACTIVO;
    @Column(name = "observacion_inicio", columnDefinition = "text")
    private String observacionInicio;
    @Column(name = "observacion_fin", columnDefinition = "text")
    private String observacionFin;
    @Version
    private Long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
