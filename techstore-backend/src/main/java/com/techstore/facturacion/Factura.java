package com.techstore.facturacion;

import com.techstore.comun.ConMoneda;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.usuarios.Usuario;
import com.techstore.ventas.Pedido;
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
@Table(name = "facturas")
@Getter
@Setter
@NoArgsConstructor
public class Factura implements ConMoneda {

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

    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    /**
     * Código de seguridad (dCodSeg) del Documento Electrónico. Forma parte del CDC,
     * así que se fija al emitir la factura y no se regenera: el CDC debe ser el mismo
     * en el panel, en "Mis pedidos" y en el PDF que se manda por correo.
     */
    @Column(name = "codigo_seguridad", nullable = false, length = 9)
    private String codigoSeguridad;

    /**
     * Receptor congelado al emitir: RUC nulo significa que se facturó a
     * consumidor final. No se recalcula desde el usuario, porque si el cliente
     * después corrige sus datos fiscales la factura ya emitida no debe cambiar.
     */
    @Column(name = "receptor_ruc", length = 20)
    private String receptorRuc;

    @Column(name = "receptor_nombre", length = 255)
    private String receptorNombre;

    /**
     * Correo del receptor. Si está cargado se le manda ahí la factura y va en el
     * dEmailRec del Documento Electrónico; si no, se usa el del dueño del pedido.
     */
    @Column(name = "receptor_email", length = 255)
    private String receptorEmail;

    @Column(nullable = false, length = 10)
    private String tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    private LocalDateTime fecha = LocalDateTime.now();

    /**
     * Moneda de la factura, congelada al emitirla. Si alguna vez se factura en
     * otra moneda hay que fijarla antes que los importes: los setters exigen que
     * cada importe venga en la moneda del documento.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "moneda", nullable = false)
    private Moneda moneda = Moneda.porDefecto();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal iva;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_factura")
    private EstadoFactura estado = EstadoFactura.Emitida;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Dinero getSubtotal() {
        return importe(subtotal);
    }

    public void setSubtotal(Dinero subtotal) {
        this.subtotal = monto(subtotal);
    }

    public Dinero getIva() {
        return importe(iva);
    }

    public void setIva(Dinero iva) {
        this.iva = monto(iva);
    }

    public Dinero getTotal() {
        return importe(total);
    }

    public void setTotal(Dinero total) {
        this.total = monto(total);
    }
}
