package com.techstore.envios;

import com.techstore.comun.ConMoneda;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tarifas_envio")
@Getter
@Setter
@NoArgsConstructor
public class TarifaEnvio implements ConMoneda {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(nullable = false, length = 80)
    private String codigo;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(length = 300)
    private String descripcion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private TipoEnvio tipo;
    @Column(name = "provider_code", nullable = false, length = 80)
    private String providerCode = "DATABASE";
    @Column(length = 120)
    private String transportista;
    @Column(length = 80)
    private String departamento;
    @Column(length = 100)
    private String ciudad;
    @Column(length = 100)
    private String zona;
    @Column(name = "peso_min_kg", precision = 10, scale = 3)
    private BigDecimal pesoMinKg;
    @Column(name = "peso_max_kg", precision = 10, scale = 3)
    private BigDecimal pesoMaxKg;
    @Column(name = "subtotal_min", precision = 15, scale = 2)
    private BigDecimal subtotalMin;
    @Column(name = "subtotal_max", precision = 15, scale = 2)
    private BigDecimal subtotalMax;
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal costo = BigDecimal.ZERO;
    @Column(name = "umbral_envio_gratis", precision = 15, scale = 2)
    private BigDecimal umbralEnvioGratis;
    @Column(nullable = false, precision = 3)
    private BigDecimal iva = BigDecimal.TEN;
    @Column(name = "dias_estimados_min", nullable = false)
    private Integer diasEstimadosMin = 0;
    @Column(name = "dias_estimados_max", nullable = false)
    private Integer diasEstimadosMax = 0;
    @Column(nullable = false)
    private Integer prioridad = 100;
    @Column(nullable = false)
    private Boolean promocion = false;
    @Column(name = "vigencia_desde")
    private LocalDateTime vigenciaDesde;
    @Column(name = "vigencia_hasta")
    private LocalDateTime vigenciaHasta;
    @Column(nullable = false)
    private Boolean activa = true;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Override public Moneda getMoneda() { return Moneda.porDefecto(); }
    public Dinero getCosto() { return importe(costo); }
    public void setCosto(Dinero costo) { this.costo = monto(costo); }
}
