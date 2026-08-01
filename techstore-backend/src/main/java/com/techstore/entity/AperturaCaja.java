package com.techstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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

/**
 * Apertura de un turno de caja: deja registrado con cuánto efectivo arranca el
 * cajón. Mientras {@code cierre} sea nulo, el turno está en curso; al cerrarlo
 * se enlaza con su {@link CierreCaja}.
 */
@Entity
@Table(name = "aperturas_caja")
@Getter
@Setter
@NoArgsConstructor
public class AperturaCaja implements ConMoneda {

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

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Moneda del efectivo declarado en la apertura. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "moneda", nullable = false)
    private Moneda moneda = Moneda.porDefecto();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "saldo_inicial", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoInicial;

    @Column(columnDefinition = "text")
    private String notas;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cierre_id")
    private CierreCaja cierre;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Dinero getSaldoInicial() {
        return importe(saldoInicial);
    }

    public void setSaldoInicial(Dinero saldoInicial) {
        this.saldoInicial = monto(saldoInicial);
    }
}
