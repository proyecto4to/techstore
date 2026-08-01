package com.techstore.ventas;

import com.techstore.catalogo.Producto;
import com.techstore.comun.BusinessException;
import com.techstore.comun.ConMoneda;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

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

    /**
     * Descuento <b>por unidad</b>, no por línea. Así lo define el manual técnico
     * del SIFEN (EA002, "descuento particular sobre el precio unitario") y así
     * entra en el cálculo del total del ítem. El {@link #subtotal} ya lo tiene
     * descontado.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

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

    /**
     * Carga el precio de la línea y deriva el subtotal, que es
     * {@code (precio − descuento) × cantidad}.
     *
     * <p>No hay un {@code setSubtotal}: el subtotal no es un dato que se cargue
     * sino el resultado de los otros tres. Si se pudiera fijar aparte, alcanzaría
     * un descuento aplicado a medias para que la línea diga un importe y el
     * documento electrónico calcule otro.
     *
     * @param descuento por unidad, no por línea. {@code null} es sin descuento.
     */
    public void fijarPrecio(Dinero precioUnitario, Dinero descuento) {
        Dinero rebaja = descuento == null ? Dinero.cero(getMoneda()) : descuento;
        if (rebaja.esNegativo()) {
            throw new BusinessException("ventas.descuento.negativo", producto.getNombre());
        }
        if (rebaja.mayorQue(precioUnitario)) {
            throw new BusinessException("ventas.descuento.mayor.al.precio",
                    producto.getNombre(), rebaja.plano(), precioUnitario.plano());
        }
        this.precioUnitario = monto(precioUnitario);
        this.descuento = monto(rebaja);
        this.subtotal = monto(precioUnitario.menos(rebaja).por(cantidad));
    }

    public Dinero getPrecioUnitario() {
        return importe(precioUnitario);
    }

    /** Descuento por unidad. */
    public Dinero getDescuento() {
        return importe(descuento);
    }

    /** Lo que el descuento le saca a la línea entera: útil para el total del documento. */
    public Dinero getDescuentoDeLaLinea() {
        return getDescuento().por(cantidad);
    }

    public Dinero getSubtotal() {
        return importe(subtotal);
    }
}
