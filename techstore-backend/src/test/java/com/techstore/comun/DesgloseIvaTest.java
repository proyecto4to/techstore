package com.techstore.comun;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesgloseIvaTest {

    /** Una línea cualquiera con IVA, sin arrastrar una entidad JPA al test. */
    private static final class Linea implements LineaGravada {

        private final Dinero subtotal;
        private final BigDecimal tasa;

        private Linea(long monto, Integer tasa) {
            this.subtotal = Dinero.de(monto, Moneda.PYG);
            this.tasa = tasa == null ? null : BigDecimal.valueOf(tasa);
        }

        static Linea de(long monto, Integer tasa) {
            return new Linea(monto, tasa);
        }

        @Override
        public Dinero getSubtotal() {
            return subtotal;
        }

        @Override
        public BigDecimal getIvaAplicado() {
            return tasa;
        }
    }

    @Test
    void repartePorTasaYCadaCanastaSumaLoSuyo() {
        DesgloseIva d = DesgloseIva.de(Moneda.PYG, List.of(
                Linea.de(1_100_000, 10),
                Linea.de(220_000, 10),
                Linea.de(210_000, 5),
                Linea.de(50_000, 0)));

        assertEquals(Dinero.de(1_320_000, Moneda.PYG), d.gravado10());
        assertEquals(Dinero.de(210_000, Moneda.PYG), d.gravado5());
        assertEquals(Dinero.de(50_000, Moneda.PYG), d.exento());
        assertEquals(Dinero.de(1_580_000, Moneda.PYG), d.total());
    }

    @Test
    void elIvaSeExtraeNoSeSuma() {
        // Los precios ya lo traen adentro: 1.100.000 al 10% contiene 100.000 de
        // IVA, no 110.000. Y al 5%, 210.000 contiene 10.000.
        DesgloseIva d = DesgloseIva.de(Moneda.PYG, List.of(
                Linea.de(1_100_000, 10),
                Linea.de(210_000, 5)));

        assertEquals(Dinero.de(100_000, Moneda.PYG), d.iva10());
        assertEquals(Dinero.de(10_000, Moneda.PYG), d.iva5());
        assertEquals(Dinero.de(110_000, Moneda.PYG), d.iva());
    }

    @Test
    void loExentoNoAportaIva() {
        DesgloseIva d = DesgloseIva.de(Moneda.PYG, List.of(Linea.de(500_000, 0)));

        assertEquals(Dinero.cero(Moneda.PYG), d.iva());
        assertEquals(Dinero.de(500_000, Moneda.PYG), d.exento());
    }

    @Test
    void unaTasaDesconocidaCuentaComoExentaEnVezDeDesaparecer() {
        // Si una tasa mal cargada no cayera en ninguna canasta, el total del
        // desglose no daría el del documento y el descuadre pasaría inadvertido.
        DesgloseIva d = DesgloseIva.de(Moneda.PYG, List.of(Linea.de(300_000, 21)));

        assertEquals(Dinero.de(300_000, Moneda.PYG), d.exento());
        assertEquals(Dinero.de(300_000, Moneda.PYG), d.total());
        assertEquals(Dinero.cero(Moneda.PYG), d.iva());
    }

    @Test
    void unaTasaNulaEsExenta() {
        DesgloseIva d = DesgloseIva.cero(Moneda.PYG).mas(null, Dinero.de(80_000, Moneda.PYG));

        assertEquals(Dinero.de(80_000, Moneda.PYG), d.exento());
    }

    @Test
    void sinLineasDaCeroEnLasTres() {
        DesgloseIva d = DesgloseIva.de(Moneda.PYG, List.of());

        assertEquals(Dinero.cero(Moneda.PYG), d.total());
        assertEquals(Dinero.cero(Moneda.PYG), d.iva());
    }
}
