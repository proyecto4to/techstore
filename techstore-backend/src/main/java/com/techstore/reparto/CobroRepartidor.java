package com.techstore.reparto;

import com.techstore.comun.ConMoneda;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.usuarios.Usuario;
import com.techstore.ventas.Pedido;
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
@Table(name = "cobros_repartidor")
@Getter
@Setter
@NoArgsConstructor
public class CobroRepartidor implements ConMoneda {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asignacion_id", nullable = false)
    private AsignacionReparto asignacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "repartidor_id", nullable = false)
    private Usuario repartidor;
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "moneda")
    private Moneda moneda = Moneda.porDefecto();
    @Column(name = "medio_pago", nullable = false, length = 30)
    private String medioPago = "Efectivo";
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "registrado_por_id", nullable = false)
    private Usuario registradoPor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EstadoMovimientoCaja estado = EstadoMovimientoCaja.REGISTRADO;
    @Column(nullable = false, length = 100)
    private String referencia;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Dinero getMonto() { return importe(monto); }
    public void setMonto(Dinero valor) { monto = monto(valor); }
}
