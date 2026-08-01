package com.techstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cierres_caja")
@Getter
@Setter
@NoArgsConstructor
public class CierreCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Empresa duena de la fila. Hibernate lo completa al insertar y lo agrega al
     * WHERE de cada consulta, asi el aislamiento no depende de que nadie se
     * olvide de filtrar.
     */
    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, unique = true)
    private Integer numero;

    @Column(name = "fecha_desde", nullable = false)
    private LocalDateTime fechaDesde;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Efectivo con el que arrancó el turno, declarado en la apertura. */
    @Column(name = "saldo_inicial", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    @Column(name = "ingresos_efectivo", nullable = false, precision = 15, scale = 2)
    private BigDecimal ingresosEfectivo;

    @Column(name = "egresos_efectivo", nullable = false, precision = 15, scale = 2)
    private BigDecimal egresosEfectivo;

    @Column(name = "ingresos_transferencia", nullable = false, precision = 15, scale = 2)
    private BigDecimal ingresosTransferencia;

    /** Salidas por transferencia (p. ej. pagos a proveedor): no tocan el cajón, van al banco. */
    @Column(name = "egresos_transferencia", nullable = false, precision = 15, scale = 2)
    private BigDecimal egresosTransferencia = BigDecimal.ZERO;

    @Column(name = "efectivo_teorico", nullable = false, precision = 15, scale = 2)
    private BigDecimal efectivoTeorico;

    @Column(name = "efectivo_contado", nullable = false, precision = 15, scale = 2)
    private BigDecimal efectivoContado;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal diferencia;

    @Column(columnDefinition = "text")
    private String notas;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
