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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.TenantId;
import com.techstore.util.Dinero;
import com.techstore.util.Moneda;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cierres_caja")
@Getter
@Setter
@NoArgsConstructor
public class CierreCaja implements ConMoneda {

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

    /** Moneda del arqueo. Todos sus importes están en esta. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "moneda", nullable = false)
    private Moneda moneda = Moneda.porDefecto();

    /** Efectivo con el que arrancó el turno, declarado en la apertura. */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "saldo_inicial", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "ingresos_efectivo", nullable = false, precision = 15, scale = 2)
    private BigDecimal ingresosEfectivo;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "egresos_efectivo", nullable = false, precision = 15, scale = 2)
    private BigDecimal egresosEfectivo;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "ingresos_transferencia", nullable = false, precision = 15, scale = 2)
    private BigDecimal ingresosTransferencia;

    /** Salidas por transferencia (p. ej. pagos a proveedor): no tocan el cajón, van al banco. */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "egresos_transferencia", nullable = false, precision = 15, scale = 2)
    private BigDecimal egresosTransferencia = BigDecimal.ZERO;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "efectivo_teorico", nullable = false, precision = 15, scale = 2)
    private BigDecimal efectivoTeorico;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "efectivo_contado", nullable = false, precision = 15, scale = 2)
    private BigDecimal efectivoContado;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal diferencia;

    @Column(columnDefinition = "text")
    private String notas;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Dinero getSaldoInicial() {
        return importe(saldoInicial);
    }

    public void setSaldoInicial(Dinero saldoInicial) {
        this.saldoInicial = monto(saldoInicial);
    }

    public Dinero getIngresosEfectivo() {
        return importe(ingresosEfectivo);
    }

    public void setIngresosEfectivo(Dinero ingresosEfectivo) {
        this.ingresosEfectivo = monto(ingresosEfectivo);
    }

    public Dinero getEgresosEfectivo() {
        return importe(egresosEfectivo);
    }

    public void setEgresosEfectivo(Dinero egresosEfectivo) {
        this.egresosEfectivo = monto(egresosEfectivo);
    }

    public Dinero getIngresosTransferencia() {
        return importe(ingresosTransferencia);
    }

    public void setIngresosTransferencia(Dinero ingresosTransferencia) {
        this.ingresosTransferencia = monto(ingresosTransferencia);
    }

    public Dinero getEgresosTransferencia() {
        return importe(egresosTransferencia);
    }

    public void setEgresosTransferencia(Dinero egresosTransferencia) {
        this.egresosTransferencia = monto(egresosTransferencia);
    }

    public Dinero getEfectivoTeorico() {
        return importe(efectivoTeorico);
    }

    public void setEfectivoTeorico(Dinero efectivoTeorico) {
        this.efectivoTeorico = monto(efectivoTeorico);
    }

    public Dinero getEfectivoContado() {
        return importe(efectivoContado);
    }

    public void setEfectivoContado(Dinero efectivoContado) {
        this.efectivoContado = monto(efectivoContado);
    }

    public Dinero getDiferencia() {
        return importe(diferencia);
    }

    public void setDiferencia(Dinero diferencia) {
        this.diferencia = monto(diferencia);
    }
}
