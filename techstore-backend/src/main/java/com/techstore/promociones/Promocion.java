package com.techstore.promociones;

import com.techstore.catalogo.Marca;
import com.techstore.catalogo.Producto;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un descuento porcentual con fecha de inicio y de fin.
 *
 * <p>La vigencia tiene dos partes que no hay que confundir: {@code activa} es
 * el interruptor que maneja administración, y la ventana de fechas es cuándo
 * corresponde aplicarla. Una promoción cargada para la semana que viene está
 * activa y todavía no rige.
 */
@Entity
@Table(name = "promociones")
@Getter
@Setter
@NoArgsConstructor
public class Promocion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** Cómo la reconoce quien administra: «Black Friday», «Liquidación». */
    @Column(nullable = false, length = 120)
    private String nombre;

    /** Cuánto descuenta, de 0 exclusivo a 90 inclusive. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlcancePromocion alcance;

    /** Solo con alcance {@link AlcancePromocion#PRODUCTO}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /** Solo con alcance {@link AlcancePromocion#MARCA}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;

    @Column(nullable = false)
    private LocalDateTime desde;

    @Column(nullable = false)
    private LocalDateTime hasta;

    @Column(nullable = false)
    private Boolean activa = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Si corresponde aplicarla en este momento. */
    public boolean vigenteEn(LocalDateTime momento) {
        return Boolean.TRUE.equals(activa)
                && !momento.isBefore(desde)
                && !momento.isAfter(hasta);
    }

    /** Si alcanza a este producto, ya sea por sí mismo o por su marca. */
    public boolean alcanzaA(Producto candidato) {
        return switch (alcance) {
            case TODOS -> true;
            case PRODUCTO -> producto != null && producto.getId().equals(candidato.getId());
            case MARCA -> marca != null
                    && candidato.getMarca() != null
                    && marca.getId().equals(candidato.getMarca().getId());
        };
    }
}
