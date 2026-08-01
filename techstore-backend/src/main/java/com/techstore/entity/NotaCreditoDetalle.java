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
import com.techstore.util.Dinero;
import com.techstore.util.Moneda;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "nota_credito_detalles")
@Getter
@Setter
@NoArgsConstructor
public class NotaCreditoDetalle implements ConMoneda {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_credito_id", nullable = false)
    private NotaCredito notaCredito;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    /**
     * Tasa de IVA en porcentaje (10, 5 o 0). No es dinero: es el porcentaje con
     * el que se calcula el impuesto contenido en el subtotal.
     */
    @Column(name = "iva_aplicado", nullable = false, precision = 3)
    private BigDecimal ivaAplicado;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * La de la nota de crédito: un detalle no tiene moneda propia. Por eso hay
     * que agregarlo a la nota antes de cargarle importes.
     */
    @Override
    public Moneda getMoneda() {
        if (notaCredito == null) {
            throw new IllegalStateException(
                    "El detalle todavía no pertenece a ninguna nota de crédito. Agregalo con "
                            + "NotaCredito.agregarDetalle(...) antes de cargarle importes: la moneda la define la nota.");
        }
        return notaCredito.getMoneda();
    }

    public Dinero getPrecioUnitario() {
        return importe(precioUnitario);
    }

    public void setPrecioUnitario(Dinero precioUnitario) {
        this.precioUnitario = monto(precioUnitario);
    }

    public Dinero getSubtotal() {
        return importe(subtotal);
    }

    public void setSubtotal(Dinero subtotal) {
        this.subtotal = monto(subtotal);
    }
}
