package com.techstore.facturacion;

import com.techstore.comun.CondicionOperacion;
import com.techstore.comun.ConMoneda;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.TipoIdentificacion;
import com.techstore.usuarios.Usuario;
import com.techstore.ventas.Pedido;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    /**
     * Dirección y teléfono del receptor, congelados al emitir como el resto de
     * sus datos. Nulos cuando no se informaron: el KuDE entonces no imprime esas
     * líneas, en vez de dejar una raya que no dice nada.
     */
    @Column(name = "receptor_direccion", length = 255)
    private String receptorDireccion;

    @Column(name = "receptor_telefono", length = 20)
    private String receptorTelefono;

    /**
     * Con qué documento se identificó el receptor. Decide cómo se lo declara en
     * el Registro de Comprobantes de la DNIT: sin RUC la venta va como "sin
     * nombre", que es lo que la administración entiende por consumidor final.
     */
    @Convert(converter = TipoIdentificacion.ConversorJpa.class)
    @Column(name = "receptor_tipo_documento", nullable = false)
    private TipoIdentificacion receptorTipoDocumento = TipoIdentificacion.SinNombre;

    /**
     * Timbrado con el que salió, congelado al emitir. Un timbrado vence y se
     * reemplaza; si el libro de IVA de enero se imprime en diciembre tiene que
     * mostrar el que la factura tenía en enero, no el vigente hoy.
     *
     * <p>Nulo solo en las facturas emitidas antes de que el sistema lo
     * registrara: para esas, el único timbrado que hubo es el de la
     * configuración (ver {@link #timbradoO(String)}).
     */
    @Column(length = 8)
    private String timbrado;

    /**
     * Contado o crédito. Hoy el sistema solo cobra al contado, pero es un dato
     * del documento y no una constante: el KuDE lo imprime y la DNIT lo pide en
     * el campo 13 del Registro de Comprobantes.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "condicion_venta", columnDefinition = "condicion_operacion", nullable = false)
    private CondicionOperacion condicionVenta = CondicionOperacion.Contado;

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

    /**
     * El timbrado de esta factura, o el que se le pase si es anterior a que el
     * sistema empezara a guardarlo. El respaldo tiene que venir de afuera
     * porque es el de la configuración de la empresa, que la entidad no conoce.
     */
    public String timbradoO(String respaldo) {
        return timbrado == null || timbrado.isBlank() ? respaldo : timbrado;
    }

    /** Si se cobró al momento. Lo usan el KuDE y la factura impresa. */
    public boolean esContado() {
        return condicionVenta == CondicionOperacion.Contado;
    }
}
