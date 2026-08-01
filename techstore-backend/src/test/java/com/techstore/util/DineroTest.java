package com.techstore.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DineroTest {

    private static Dinero gs(long monto) {
        return Dinero.de(monto, Moneda.PYG);
    }

    private static Dinero gs(String monto) {
        return Dinero.de(new BigDecimal(monto), Moneda.PYG);
    }

    // --- La moneda manda sobre los decimales -------------------------------

    @Test
    void elGuaraniNoTieneCentavos() {
        assertEquals("1500000", gs("1500000.49").plano());
        assertEquals(0, gs(1_500_000).monto().scale());
    }

    @Test
    void elDolarSiTieneCentavos() {
        Dinero usd = Dinero.de(new BigDecimal("10.499"), Moneda.USD);
        assertEquals("10.50", usd.plano());
    }

    @Test
    void redondeaComercialmente() {
        // 0,5 sube: es el redondeo que espera una factura, y el que ya usaba el KuDE.
        assertEquals("101", gs("100.5").plano());
        assertEquals("100", gs("100.4").plano());
        // También hacia abajo cuando el importe es negativo (una devolución).
        assertEquals("-101", gs("-100.5").plano());
    }

    @Test
    void dosImportesIgualesSonIgualesAunqueVenganConDistintaEscala() {
        // Sin normalizar la escala, BigDecimal diría que 1000 y 1000,00 son distintos
        // y dos importes idénticos no entrarían en el mismo grupo de un Map.
        assertEquals(gs("1000"), gs("1000.00"));
        assertEquals(gs("1000").hashCode(), gs("1000.00").hashCode());
    }

    // --- Aritmética ---------------------------------------------------------

    @Test
    void sumaYResta() {
        assertEquals(gs(1_800_000), gs(1_500_000).mas(gs(300_000)));
        assertEquals(gs(1_200_000), gs(1_500_000).menos(gs(300_000)));
    }

    @Test
    void multiplicaPorUnaCantidadDeUnidades() {
        assertEquals(gs(4_500_000), gs(1_500_000).por(3));
    }

    @Test
    void sumaUnaColeccionVaciaSinPerderLaMoneda() {
        // El caso que rompe un reduce() ingenuo: sin elementos hay que saber igual
        // en qué moneda está el cero.
        Dinero total = Dinero.sumar(Moneda.PYG, List.of());
        assertTrue(total.esCero());
        assertEquals(Moneda.PYG, total.moneda());
    }

    @Test
    void sumaUnaColeccion() {
        assertEquals(gs(600_000), Dinero.sumar(Moneda.PYG, List.of(gs(100_000), gs(200_000), gs(300_000))));
    }

    @Test
    void nuloSeTrataComoCeroSoloCuandoSePideExplicitamente() {
        assertEquals(gs(0), Dinero.deONulo(null, Moneda.PYG));
        assertThrows(NullPointerException.class, () -> Dinero.de((BigDecimal) null, Moneda.PYG));
    }

    // --- No se mezclan monedas ----------------------------------------------

    @Test
    void noSePuedenSumarMonedasDistintas() {
        Dinero guaranies = gs(1_000_000);
        Dinero dolares = Dinero.de(100, Moneda.USD);

        assertThrows(Dinero.MonedasIncompatiblesException.class, () -> guaranies.mas(dolares));
        assertThrows(Dinero.MonedasIncompatiblesException.class, () -> guaranies.menos(dolares));
        assertThrows(Dinero.MonedasIncompatiblesException.class, () -> guaranies.compareTo(dolares));
    }

    @Test
    void elMensajeDiceQueMonedasSeIntentaronMezclar() {
        Dinero.MonedasIncompatiblesException error = assertThrows(
                Dinero.MonedasIncompatiblesException.class,
                () -> gs(1).mas(Dinero.de(1, Moneda.USD)));
        assertTrue(error.getMessage().contains("PYG"));
        assertTrue(error.getMessage().contains("USD"));
    }

    @Test
    void importesIgualesEnDistintaMonedaNoSonElMismoImporte() {
        assertFalse(gs(100).equals(Dinero.de(100, Moneda.USD)));
    }

    // --- IVA incluido -------------------------------------------------------

    @Test
    void extraeElIvaDel10PorCiento() {
        // Precio con IVA incluido: el impuesto no se suma, se saca de adentro.
        // 1.500.000 × 10 ÷ 110 = 136.363,63… -> 136.364
        assertEquals(gs(136_364), gs(1_500_000).ivaIncluido(BigDecimal.TEN));
    }

    @Test
    void extraeElIvaDel5PorCiento() {
        // 1.500.000 × 5 ÷ 105 = 71.428,57… -> 71.429
        assertEquals(gs(71_429), gs(1_500_000).ivaIncluido(BigDecimal.valueOf(5)));
    }

    @Test
    void loExentoNoTieneIva() {
        assertTrue(gs(1_500_000).ivaIncluido(BigDecimal.ZERO).esCero());
        assertTrue(gs(1_500_000).ivaIncluido(null).esCero());
        assertTrue(gs(1_500_000).ivaIncluido(BigDecimal.valueOf(-10)).esCero());
    }

    @Test
    void elIvaCalculadoEsElMismoQueImprimeElKude() {
        // Este es el defecto que motivó el tipo: el IVA se guardaba con dos
        // decimales y se imprimía dividiendo por 11 y por 21 a cero decimales,
        // así que la factura guardada y el documento impreso podían no coincidir.
        // Ahora tienen que dar exactamente lo mismo para cualquier importe.
        BigDecimal once = BigDecimal.valueOf(11);
        BigDecimal veintiuno = BigDecimal.valueOf(21);

        for (long monto : new long[]{1, 7, 999, 100_000, 1_500_000, 3_333_333, 987_654_321}) {
            BigDecimal comoLoImprimeElKude10 = BigDecimal.valueOf(monto).divide(once, 0, RoundingMode.HALF_UP);
            BigDecimal comoLoImprimeElKude5 = BigDecimal.valueOf(monto).divide(veintiuno, 0, RoundingMode.HALF_UP);

            assertEquals(comoLoImprimeElKude10, gs(monto).ivaIncluido(BigDecimal.TEN).monto(),
                    "IVA 10% de " + monto);
            assertEquals(comoLoImprimeElKude5, gs(monto).ivaIncluido(BigDecimal.valueOf(5)).monto(),
                    "IVA 5% de " + monto);
        }
    }

    @Test
    void loGravadoMasElIvaDevuelveElTotal() {
        // La identidad que tiene que cumplir toda factura: gravadas + IVA = total.
        // Si el redondeo se hiciera por separado en cada parte, no cerraría.
        Dinero total = gs(1_500_000);
        Dinero iva = total.ivaIncluido(BigDecimal.TEN);
        assertEquals(total, total.sinIva(BigDecimal.TEN).mas(iva));
    }

    // --- Comparaciones ------------------------------------------------------

    @Test
    void compara() {
        assertTrue(gs(200).mayorQue(gs(100)));
        assertTrue(gs(100).menorQue(gs(200)));
        assertFalse(gs(100).mayorQue(gs(100)));
        assertEquals(0, gs(100).compareTo(gs("100.00")));
    }

    @Test
    void conoceSuSigno() {
        assertTrue(Dinero.cero(Moneda.PYG).esCero());
        assertTrue(gs(1).esPositivo());
        assertTrue(gs(-1).esNegativo());
        assertEquals(gs(-100), gs(100).negado());
        assertEquals(gs(100), gs(-100).absoluto());
    }

    @Test
    void seImprimeConElSimboloDeSuMoneda() {
        assertEquals("Gs. 1500000", gs(1_500_000).toString());
        assertEquals("US$ 100.00", Dinero.de(100, Moneda.USD).toString());
    }
}
