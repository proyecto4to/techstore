package com.techstore.vehiculos;

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
@Table(name = "vehiculos")
@Getter
@Setter
@NoArgsConstructor
public class Vehiculo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(nullable = false, length = 20)
    private String matricula;
    @Column(nullable = false, length = 80)
    private String marca;
    @Column(nullable = false, length = 80)
    private String modelo;
    private Integer anio;
    @Column(nullable = false, length = 40)
    private String tipo;
    @Column(length = 40)
    private String color;
    @Column(name = "numero_chasis", length = 100)
    private String numeroChasis;
    @Column(name = "kilometraje_actual", nullable = false, precision = 12, scale = 1)
    private BigDecimal kilometrajeActual = BigDecimal.ZERO;
    @Column(name = "tipo_combustible", length = 40)
    private String tipoCombustible;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private EstadoVehiculo estado = EstadoVehiculo.DISPONIBLE;
    @Column(nullable = false)
    private Boolean activo = true;
    @Column(columnDefinition = "text")
    private String observaciones;
    @Column(name = "fecha_alta", nullable = false)
    private LocalDateTime fechaAlta = LocalDateTime.now();
    @Version
    private Long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
