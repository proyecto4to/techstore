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
     * aparece en el aviso de reposición. En 0 nunca avisa.
     */
    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo = 0;

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

    public Integer getStockDisponible() {
        int real = stockReal == null ? 0 : stockReal;
        int comprometido = stockComprometido == null ? 0 : stockComprometido;
        return real - comprometido;
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
