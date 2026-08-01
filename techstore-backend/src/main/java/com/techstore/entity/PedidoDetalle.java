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
@Table(name = "pedido_detalles")
@Getter
@Setter
@NoArgsConstructor
public class PedidoDetalle implements ConMoneda {

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
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

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
     * La del pedido: un detalle no tiene moneda propia. Por eso hay que agregarlo
     * al pedido antes de cargarle importes.
     */
    @Override
    public Moneda getMoneda() {
        if (pedido == null) {
            throw new IllegalStateException(
                    "El detalle todavía no pertenece a ningún pedido. Agregalo con "
                            + "Pedido.agregarDetalle(...) antes de cargarle importes: la moneda la define el pedido.");
        }
        return pedido.getMoneda();
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
