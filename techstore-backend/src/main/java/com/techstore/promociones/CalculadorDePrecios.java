package com.techstore.promociones;

import com.techstore.catalogo.Producto;
import com.techstore.comun.Dinero;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * El único lugar donde se decide cuánto cuesta un producto con promociones.
 *
 * <p>Lo usan la vidriera, el carrito y el checkout. Que sea uno solo es lo que
 * evita el problema clásico: la pantalla muestra un precio y el pedido se
 * cobra con otro. Si esta clase se equivoca, se equivoca igual en los tres
 * lados, que es preferible a que cada uno tenga su propia cuenta.
 *
 * <p>Cuando dos promociones alcanzan al mismo producto <strong>gana la de
 * mayor porcentaje y no se suman</strong>. Sumarlas deja el catálogo regalado
 * con dos promociones razonables, y el resultado dependería del orden en que
 * se cargaron.
 */
@Service
@RequiredArgsConstructor
public class CalculadorDePrecios {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final PromocionRepository repository;

    /**
     * Las promociones que rigen ahora, leídas una sola vez.
     *
     * <p>Una página de catálogo son treinta productos: resolver cada uno por su
     * cuenta serían treinta consultas iguales. Además fija el momento, así que
     * una promoción que vence en el medio del armado no deja media página con
     * descuento y la otra media sin.
     */
    @Transactional(readOnly = true)
    public PromocionesVigentes vigentes() {
        LocalDateTime momento = LocalDateTime.now();
        return new PromocionesVigentes(repository.vigentesEn(momento), momento);
    }

    /** Atajo para un solo producto. */
    @Transactional(readOnly = true)
    public PrecioVigente precioDe(Producto producto) {
        return vigentes().precioDe(producto);
    }

    /** Foto de las promociones de un instante, ya resueltas contra productos. */
    public record PromocionesVigentes(List<Promocion> promociones, LocalDateTime momento) {

        public PrecioVigente precioDe(Producto producto) {
            Dinero lista = producto.getPrecio();
            Optional<Promocion> mejor = mejorPara(producto);
            if (mejor.isEmpty()) {
                return PrecioVigente.sinPromocion(lista);
            }
            Promocion promocion = mejor.get();
            Dinero descuento = lista.por(promocion.getPorcentaje().divide(CIEN, 4, Dinero.REDONDEO));
            // El descuento nunca puede igualar al precio: un producto a cero
            // rompería la factura y `fijarPrecio` lo rechaza. Con el tope de
            // 90% del CHECK de la tabla no debería pasar, pero el precio se
            // redondea a la moneda y en importes muy chicos el redondeo manda.
            if (descuento.compareTo(lista) >= 0) {
                return PrecioVigente.sinPromocion(lista);
            }
            return new PrecioVigente(
                    lista.menos(descuento),
                    lista,
                    descuento,
                    PromocionAplicadaResponse.de(promocion));
        }

        /** La más conveniente para quien compra, si alguna alcanza al producto. */
        public Optional<Promocion> mejorPara(Producto producto) {
            return promociones.stream()
                    .filter(promocion -> promocion.alcanzaA(producto))
                    .max(Comparator.comparing(Promocion::getPorcentaje));
        }

        public boolean hayAlguna() {
            return !promociones.isEmpty();
        }
    }
}
