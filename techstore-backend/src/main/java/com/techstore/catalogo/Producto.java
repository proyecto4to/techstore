package com.techstore.catalogo;

import com.techstore.comun.ConMoneda;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
public class Producto implements ConMoneda {

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

    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "text")
    private String descripcion;

    /** Moneda del precio de lista. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "moneda", nullable = false)
    private Moneda moneda = Moneda.porDefecto();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    /**
     * Costo unitario de la última compra recibida. Nulo mientras no se haya
     * comprado nunca: se deja nulo en vez de cero porque un costo de cero diría
     * que el producto es gratis y daría márgenes del 100%.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(precision = 12, scale = 2)
    private BigDecimal costo;

    /** Cuándo se recibió esa compra. Un costo sin fecha no se puede interpretar. */
    @Column(name = "costo_actualizado_en")
    private LocalDateTime costoActualizadoEn;

    /**
     * Si el producto desaparece de la tienda cuando se queda sin stock. Es por
     * producto y no una regla global: algo que se fabrica a pedido puede querer
     * mostrarse igual.
     */
    @Column(name = "ocultar_sin_stock", nullable = false)
    private Boolean ocultarSinStock = true;

    /**
     * Si el producto se ofrece en la aplicacion de clientes.
     *
     * <p>Es distinto de {@code activo}: en falso el producto sigue existiendo,
     * sigue en el inventario y se sigue pudiendo vender por mostrador, pero no
     * aparece en el catalogo publico, la busqueda ni el detalle. Sirve para
     * sacar algo de la vidriera sin sacarlo del negocio.
     */
    @Column(name = "visible_en_app", nullable = false)
    private Boolean visibleEnApp = true;

    /**
     * Tasa de IVA en porcentaje (10, 5 o 0). No es dinero: es el porcentaje con
     * el que se calcula el impuesto contenido en el precio.
     */
    @Column(precision = 3)
    private BigDecimal iva = BigDecimal.TEN;

    @Column(name = "stock_real")
    private Integer stockReal = 0;

    @Column(name = "stock_comprometido")
    private Integer stockComprometido = 0;

    /**
     * Punto de reposición: cuando el stock disponible cae hasta acá, el producto
     * aparece en el aviso de reposición. En 0 usa el umbral general.
     */
    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo = 0;

    /** Peso unitario usado exclusivamente por las reglas configurables de envío. */
    @Column(name = "peso_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal pesoKg = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modelo_id")
    private Modelo modelo;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Dinero getPrecio() {
        return importe(precio);
    }

    public void setPrecio(Dinero precio) {
        this.precio = monto(precio);
    }

    /** Costo de la última compra, o {@code null} si nunca se compró. */
    public Dinero getCosto() {
        return costo == null ? null : importe(costo);
    }

    /**
     * Deja el costo de la última compra recibida, con su fecha. Los dos juntos
     * porque un costo sin fecha no se puede interpretar.
     */
    public void registrarCosto(Dinero costo, LocalDateTime cuando) {
        this.costo = monto(costo);
        this.costoActualizadoEn = cuando;
    }

    /**
     * Si hoy corresponde mostrarlo en la tienda. Se mide contra el stock
     * disponible y no el real: lo comprometido por pedidos pendientes ya está
     * vendido, aunque siga en el depósito.
     */
    public boolean visibleEnTienda() {
        if (!Boolean.TRUE.equals(activo) || !Boolean.TRUE.equals(visibleEnApp)) {
            return false;
        }
        return !Boolean.TRUE.equals(ocultarSinStock) || getStockDisponible() > 0;
    }

    /**
     * Cuánto deja la venta sobre el costo, o {@code null} si todavía no se
     * compró nunca. Sobre el precio sin IVA: el impuesto no es ganancia.
     */
    public Dinero getMargen() {
        Dinero costoActual = getCosto();
        return costoActual == null ? null : getPrecio().sinIva(iva).menos(costoActual);
    }

    public Integer getStockDisponible() {
        int real = stockReal == null ? 0 : stockReal;
        int comprometido = stockComprometido == null ? 0 : stockComprometido;
        return real - comprometido;
    }

    /**
     * Tope de unidades que admite una línea de pedido. Coincide con el @Max de
     * ValidarCarritoRequest: si la vidriera ofreciera más de lo que el checkout
     * acepta, el cliente elegiría una cantidad que el servidor después rechaza.
     */
    public static final int MAXIMO_POR_PEDIDO = 99;

    /**
     * Cuántas unidades puede pedir un cliente de una sola vez. Es el stock
     * disponible techado por {@link #MAXIMO_POR_PEDIDO}: con 5000 unidades
     * publica 99 y no delata el tamaño del depósito; solo cuando queda poco el
     * número coincide con el stock, que es justo cuando el cliente necesita
     * verlo para no elegir una cantidad imposible.
     */
    public int getMaxPorPedido() {
        return Math.max(0, Math.min(getStockDisponible(), MAXIMO_POR_PEDIDO));
    }

    /**
     * Umbral que se aplica a los productos sin mínimo propio. Existe para no
     * cambiar el comportamiento anterior, cuando el aviso era este número fijo
     * para todo el catálogo.
     */
    public static final int UMBRAL_POR_DEFECTO = 5;

    /**
     * Si hay que reponerlo. Se mide contra el stock disponible, no el real: lo
     * ya comprometido en pedidos pendientes está vendido aunque siga en el
     * depósito, y contarlo haría que el aviso llegue tarde.
     */
    public boolean necesitaReposicion() {
        return getStockDisponible() <= umbralEfectivo();
    }

    /** El mínimo propio del producto; si no tiene, el general. */
    public int umbralEfectivo() {
        int minimo = stockMinimo == null ? 0 : stockMinimo;
        return minimo > 0 ? minimo : UMBRAL_POR_DEFECTO;
    }
}
