package com.techstore.compras;

import com.techstore.catalogo.Producto;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Oferta vigente de un proveedor por un producto: el último precio que se le
 * conoce y si hoy lo tiene.
 *
 * <p>Se actualiza sola al recibir compras, y admite carga manual para registrar
 * una cotización de un proveedor al que todavía no se le compró. La rapidez de
 * entrega y la fiabilidad no viven acá: se calculan del historial de pedidos,
 * porque un número guardado envejece y el historial no.
 */
@Entity
@Table(name = "proveedor_productos")
@Getter
@Setter
@NoArgsConstructor
public class ProveedorProducto implements ConMoneda {

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
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Moneda del precio ofertado. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "moneda", nullable = false)
    private Moneda moneda = Moneda.porDefecto();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    /**
     * El proveedor puede quedarse sin stock sin dejar de "tener" el producto:
     * la oferta se apaga en vez de borrarse y conserva el precio de referencia.
     */
    @Column(nullable = false)
    private Boolean disponible = true;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    public Dinero getPrecio() {
        return importe(precio);
    }

    public void setPrecio(Dinero precio) {
        this.precio = monto(precio);
    }
}
