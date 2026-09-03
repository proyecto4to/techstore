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
@Table(name = "cierres_caja_repartidor")
@Getter
@Setter
@NoArgsConstructor
public class CierreCajaRepartidor implements ConMoneda {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "repartidor_id", nullable = false)
    private Usuario repartidor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cajero_id", nullable = false)
    private Usuario cajero;
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    @Column(name = "saldo_cierre", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoCierre;
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    @Column(name = "monto_solicitado", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoSolicitado;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "moneda")
    private Moneda moneda = Moneda.porDefecto();
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private EstadoTransferencia estado = EstadoTransferencia.PENDIENTE_ACEPTACION;
    @Column(columnDefinition = "text")
    private String observacion;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Dinero getSaldoCierre() { return importe(saldoCierre); }
    public void setSaldoCierre(Dinero valor) { saldoCierre = monto(valor); }
    public Dinero getMontoSolicitado() { return importe(montoSolicitado); }
    public void setMontoSolicitado(Dinero valor) { montoSolicitado = monto(valor); }
}
