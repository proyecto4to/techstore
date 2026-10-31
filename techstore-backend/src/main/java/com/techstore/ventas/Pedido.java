package com.techstore.ventas;

import com.techstore.compras.CanalEnvio;
import com.techstore.compras.Proveedor;
import com.techstore.comun.ConMoneda;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.usuarios.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
public class Pedido implements ConMoneda {

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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "tipo_pedido")
    private TipoPedido tipo;

    @Column(name = "cliente_id")
    private Long clienteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_pedido")
    private EstadoPedido estado = EstadoPedido.P;

    private LocalDateTime fecha = LocalDateTime.now();

    /**
     * Cuándo llegó la mercadería de una compra. Nulo en ventas y en compras
     * pendientes. La diferencia contra {@link #fecha} es lo que tardó el
     * proveedor, y de ahí sale el punto de reposición.
     */
    @Column(name = "fecha_recepcion")
    private LocalDateTime fechaRecepcion;

    /**
     * Cuándo se le mandó el pedido al proveedor, y por dónde. Nulos mientras no
     * se haya mandado, y solo aplican a compras.
     *
     * <p>No son un estado: que el pedido haya salido no significa que el
     * proveedor lo haya aceptado. La compra sigue Pendiente hasta que llega la
     * mercadería.
     */
    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "enviado_canal", columnDefinition = "canal_envio")
    private CanalEnvio enviadoCanal;

    /** Moneda del pedido. Sus detalles heredan esta, no tienen una propia. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "moneda", nullable = false)
    private Moneda moneda = Moneda.porDefecto();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    /** Importe logístico incluido en {@link #total}; cero para retiro gratuito. */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "costo_envio", nullable = false, precision = 15, scale = 2)
    private BigDecimal costoEnvio = BigDecimal.ZERO;

    /** IVA contenido en el servicio de entrega (0, 5 o 10). */
    @Column(name = "iva_envio", nullable = false, precision = 3)
    private BigDecimal ivaEnvio = BigDecimal.TEN;

    // Solo aplica a compras a proveedores: marca si el pago ya se registró en caja.
    @Column(nullable = false)
    private Boolean pagada = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(columnDefinition = "text")
    private String notas;

    /** Copia inmutable de la dirección elegida al confirmar el checkout. */
    @Embedded
    private DireccionPedidoSnapshot direccionEntrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_entrega", length = 40)
    private MetodoEntrega metodoEntrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago_checkout", length = 40)
    private MetodoPagoCheckout metodoPago;

    /** Verificación del pago por transferencia. Ver {@link EstadoPago}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", nullable = false, length = 30)
    private EstadoPago estadoPago = EstadoPago.NO_REQUERIDO;

    /** Por qué la tienda no validó el último comprobante. Nulo si no hubo rechazo. */
    @Column(name = "pago_rechazo_motivo", length = 500)
    private String pagoRechazoMotivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_verificado_por")
    private Usuario pagoVerificadoPor;

    @Column(name = "pago_verificado_en")
    private LocalDateTime pagoVerificadoEn;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "idempotency_request_hash", length = 64)
    private String idempotencyRequestHash;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PedidoDetalle> detalles = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void agregarDetalle(PedidoDetalle detalle) {
        detalle.setPedido(this);
        detalles.add(detalle);
    }

    public Dinero getTotal() {
        return importe(total);
    }

    public void setTotal(Dinero total) {
        this.total = monto(total);
    }

    public Dinero getCostoEnvio() {
        return importe(costoEnvio);
    }

    public void setCostoEnvio(Dinero costoEnvio) {
        this.costoEnvio = monto(costoEnvio);
    }

    public Dinero getSubtotalProductos() {
        return getTotal().menos(getCostoEnvio());
    }
}
