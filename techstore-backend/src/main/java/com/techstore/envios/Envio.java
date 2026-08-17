package com.techstore.envios;

import com.techstore.comun.ConMoneda;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.direcciones.DireccionEntrega;
import com.techstore.ventas.Pedido;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "envios")
@Getter
@Setter
@NoArgsConstructor
public class Envio implements ConMoneda {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "direccion_entrega_id")
    private DireccionEntrega direccionEntrega;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tarifa_envio_id")
    private TarifaEnvio tarifa;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private TipoEnvio tipo;
    @Column(name = "provider_code", nullable = false, length = 80)
    private String providerCode;
    @Column(length = 120)
    private String transportista;
    @Column(name = "codigo_seguimiento", length = 120)
    private String codigoSeguimiento;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "moneda")
    private Moneda moneda = Moneda.porDefecto();
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal costo = BigDecimal.ZERO;
    @Column(nullable = false, precision = 3)
    private BigDecimal iva = BigDecimal.TEN;
    @Column(name = "fecha_estimada_desde")
    private LocalDate fechaEstimadaDesde;
    @Column(name = "fecha_estimada_hasta")
    private LocalDate fechaEstimadaHasta;
    @Column(name = "fecha_despacho")
    private LocalDateTime fechaDespacho;
    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private EstadoEnvio estado = EstadoEnvio.PENDIENTE;
    @Column(columnDefinition = "text")
    private String observaciones;
    @OneToMany(mappedBy = "envio", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fechaHora ASC, id ASC")
    private List<EnvioEvento> eventos = new ArrayList<>();
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Dinero getCosto() { return importe(costo); }
    public void setCosto(Dinero costo) { this.costo = monto(costo); }
    public void agregarEvento(EnvioEvento evento) {
        evento.setEnvio(this);
        eventos.add(evento);
    }
}
