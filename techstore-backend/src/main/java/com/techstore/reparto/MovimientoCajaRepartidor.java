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
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimientos_caja_repartidor")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoCajaRepartidor implements ConMoneda {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "repartidor_id", nullable = false)
    private Usuario repartidor;
    @Enumerated(EnumType.STRING) @Column(name = "tipo_movimiento", nullable = false, length = 40)
    private TipoMovimientoCajaRepartidor tipoMovimiento;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private NaturalezaMovimiento naturaleza;
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "moneda")
    private Moneda moneda = Moneda.porDefecto();
    @Column(name = "referencia_tipo", length = 40)
    private String referenciaTipo;
    @Column(name = "referencia_id")
    private Long referenciaId;
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EstadoMovimientoCaja estado = EstadoMovimientoCaja.REGISTRADO;
    @Column(columnDefinition = "text")
    private String observacion;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Dinero getMonto() { return importe(monto); }
    public void setMonto(Dinero valor) { monto = monto(valor); }
}
