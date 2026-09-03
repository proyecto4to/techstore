package com.techstore.reparto;

import com.techstore.comun.ConMoneda;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.usuarios.Usuario;
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
import java.time.LocalDateTime;

@Entity
@Table(name = "transferencias_repartidor_cajero")
@Getter
@Setter
@NoArgsConstructor
public class TransferenciaRepartidorCajero implements ConMoneda {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "repartidor_id", nullable = false)
    private Usuario repartidor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cajero_id", nullable = false)
    private Usuario cajero;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cierre_id", nullable = false)
    private CierreCajaRepartidor cierre;
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "moneda")
    private Moneda moneda = Moneda.porDefecto();
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private EstadoTransferencia estado = EstadoTransferencia.PENDIENTE_ACEPTACION;
    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();
    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "respondido_por_id")
    private Usuario respondidoPor;
    @Column(name = "motivo_rechazo", columnDefinition = "text")
    private String motivoRechazo;
    @Column(columnDefinition = "text")
    private String observacion;
    @Version
    private Long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Dinero getMonto() { return importe(monto); }
    public void setMonto(Dinero valor) { monto = monto(valor); }
}
