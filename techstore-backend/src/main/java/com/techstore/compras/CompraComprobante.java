package com.techstore.compras;

import com.techstore.comun.CondicionOperacion;
import com.techstore.comun.ConMoneda;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.TipoComprobante;
import com.techstore.comun.TipoIdentificacion;
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
import jakarta.persistence.OneToOne;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * El comprobante fiscal que emitió el proveedor: la factura de papel que llega
 * con la mercadería.
 *
 * <p>No es lo mismo que la compra. La compra es un pedido interno —qué se le
 * pidió a quién y a qué precio— y existe desde antes de que haya documento
 * alguno. Esto es el documento del proveedor, con su timbrado y su número, que
 * es lo único que se asienta en el libro de compras y lo único que da crédito
 * fiscal ante la DNIT.
 *
 * <p>Los importes se guardan <b>con el IVA incluido</b> y abiertos por tasa,
 * que es exactamente como los pide el Registro de Comprobantes. El impuesto no
 * se guarda: se extrae con {@link Dinero#ivaIncluido}, para que no pueda
 * contradecir al monto del que sale.
 */
@Entity
@Table(name = "compra_comprobantes")
@Getter
@Setter
@NoArgsConstructor
public class CompraComprobante implements ConMoneda {

    /** Las dos tasas de IVA que hay en Paraguay. */
    public static final BigDecimal TASA_10 = BigDecimal.TEN;
    public static final BigDecimal TASA_5 = BigDecimal.valueOf(5);

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

    /** La compra que este documento respalda. Una por comprobante. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Convert(converter = TipoComprobante.ConversorJpa.class)
    @Column(name = "tipo_comprobante", nullable = false)
    private TipoComprobante tipoComprobante = TipoComprobante.Factura;

    /** Nulo solo en el despacho de importación, que no lleva timbrado. */
    @Column(length = 8)
    private String timbrado;

    /** Nulo solo donde la DNIT no lo exige: ticket, boleto aéreo, despacho. */
    @Column(length = 20)
    private String numero;

    /**
     * La del papel, que no tiene por qué ser la de recepción: se compra el 28,
     * llega el 3, y el crédito fiscal corresponde al período del comprobante.
     */
    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "condicion_operacion", nullable = false)
    private CondicionOperacion condicion = CondicionOperacion.Contado;

    /**
     * Identificación del proveedor, congelada. Si mañana corrige su razón social
     * o cambia de RUC, el libro de un período ya declarado no puede cambiar.
     */
    @Convert(converter = TipoIdentificacion.ConversorJpa.class)
    @Column(name = "tipo_identificacion", nullable = false)
    private TipoIdentificacion tipoIdentificacion = TipoIdentificacion.RUC;

    @Column(name = "proveedor_ruc", length = 20)
    private String proveedorRuc;

    @Column(name = "proveedor_razon_social", length = 255)
    private String proveedorRazonSocial;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "moneda", nullable = false)
    private Moneda moneda = Moneda.porDefecto();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "gravado_10", nullable = false, precision = 15, scale = 2)
    private BigDecimal gravado10 = BigDecimal.ZERO;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "gravado_5", nullable = false, precision = 15, scale = 2)
    private BigDecimal gravado5 = BigDecimal.ZERO;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal exento = BigDecimal.ZERO;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    /** Qué comprobante corrige, si este es una nota de crédito o de débito. */
    @Column(name = "asociado_numero", length = 20)
    private String asociadoNumero;

    @Column(name = "asociado_timbrado", length = 8)
    private String asociadoTimbrado;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Dinero getGravado10() {
        return importe(gravado10);
    }

    public void setGravado10(Dinero gravado10) {
        this.gravado10 = monto(gravado10);
    }

    public Dinero getGravado5() {
        return importe(gravado5);
    }

    public void setGravado5(Dinero gravado5) {
        this.gravado5 = monto(gravado5);
    }

    public Dinero getExento() {
        return importe(exento);
    }

    public void setExento(Dinero exento) {
        this.exento = monto(exento);
    }

    public Dinero getTotal() {
        return importe(total);
    }

    public void setTotal(Dinero total) {
        this.total = monto(total);
    }

    /** IVA contenido en lo gravado al 10%. */
    public Dinero getIva10() {
        return getGravado10().ivaIncluido(TASA_10);
    }

    /** IVA contenido en lo gravado al 5%. */
    public Dinero getIva5() {
        return getGravado5().ivaIncluido(TASA_5);
    }

    /**
     * El IVA que este comprobante da a favor. Una nota de crédito del proveedor
     * devuelve crédito fiscal, así que resta.
     */
    public Dinero getCreditoFiscal() {
        Dinero iva = getIva10().mas(getIva5());
        return tipoComprobante.esCorrectivoNegativo() ? iva.negado() : iva;
    }
}
