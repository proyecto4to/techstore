package com.techstore.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumeroALetrasTest {

    @Test
    void convierteNumerosTipicos() {
        assertEquals("cero", NumeroALetras.convertir(0));
        assertEquals("veintiuno", NumeroALetras.convertir(21));
        assertEquals("cien", NumeroALetras.convertir(100));
        assertEquals("ciento quince", NumeroALetras.convertir(115));
        assertEquals("mil", NumeroALetras.convertir(1000));
        assertEquals("veintiún mil", NumeroALetras.convertir(21000));
        assertEquals("trescientos cincuenta mil", NumeroALetras.convertir(350000));
        assertEquals("un millón", NumeroALetras.convertir(1_000_000));
        assertEquals("cuatro millones setecientos mil", NumeroALetras.convertir(4_700_000));
        assertEquals("doscientos veintisiete mil doscientos setenta y tres",
                NumeroALetras.convertir(227_273));
        assertEquals("novecientos noventa y nueve millones novecientos noventa y nueve mil novecientos noventa y nueve",
                NumeroALetras.convertir(999_999_999));
    }

    @Test
    void expresaMontosEnGuaranies() {
        assertEquals("guaraníes cuatro millones setecientos mil",
                NumeroALetras.enGuaranies(new BigDecimal("4700000.00")));
        assertEquals("guaraníes doscientos mil con 50/100",
                NumeroALetras.enGuaranies(new BigDecimal("200000.50")));
    }

    @Test
    void nombraLaMonedaEnPluralYNoEnSingular() {
        // El documento impreso dice "guaraníes cuatro millones", no "guaraní
        // cuatro millones": el nombre acompaña al importe, no a la moneda.
        assertEquals("guaraníes cuatro millones setecientos mil",
                NumeroALetras.enLetras(Dinero.de(4_700_000, Moneda.PYG)));
        assertEquals("dólares estadounidenses cien",
                NumeroALetras.enLetras(Dinero.de(100, Moneda.USD)));
    }

    @Test
    void unImporteEnGuaraniesNoLlevaFraccion() {
        // El guaraní no tiene centavos, así que nunca aparece un "con 00/100".
        assertEquals("guaraníes doscientos mil",
                NumeroALetras.enLetras(Dinero.de(200_000, Moneda.PYG)));
    }
}
