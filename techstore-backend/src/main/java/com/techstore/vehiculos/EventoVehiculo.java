package com.techstore.vehiculos;

import com.techstore.usuarios.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "eventos_vehiculo")
@Getter
@Setter
@NoArgsConstructor
public class EventoVehiculo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uso_vehiculo_id", nullable = false)
    private UsoVehiculo usoVehiculo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "vehiculo_id", nullable = false)
    private Vehiculo vehiculo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "repartidor_id", nullable = false)
    private Usuario repartidor;
    @Enumerated(EnumType.STRING) @Column(name = "tipo_evento", nullable = false, length = 40)
    private TipoEventoVehiculo tipoEvento;
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
    @Column(nullable = false, precision = 12, scale = 1)
    private BigDecimal kilometraje;
    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;
    @Column(length = 255)
    private String ubicacion;
    @Column(precision = 15, scale = 2)
    private BigDecimal importe;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "datos_adicionales", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> datosAdicionales = new LinkedHashMap<>();
    @Column(columnDefinition = "text")
    private String observacion;
    @Column(name = "requiere_atencion", nullable = false)
    private Boolean requiereAtencion = false;
    @Column(nullable = false)
    private Boolean resuelto = false;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
