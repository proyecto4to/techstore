package com.techstore.comun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RucParaguayoTest {

    @Test
    void calculaElDigitoVerificadorEnBase11() {
        // Ponderación de derecha a izquierda por 2,3,4… y módulo 11.
        // 80012345: 5*2+4*3+3*4+2*5+1*6+0*7+0*8+8*9 = 122 -> 122 % 11 = 1 -> DV 0
        assertEquals(0, RucParaguayo.calcularDv("80012345"));
    }

    @Test
    void aceptaUnRucConDigitoVerificadorCorrecto() {
        assertTrue(RucParaguayo.esValido("80012345-0"));
    }

    @Test
    void rechazaUnRucConDigitoVerificadorEquivocado() {
        // El RUC de demostración que trae application.properties tiene DV inventado:
        // su dígito real es 0, no 6. Se rechaza, que es justamente el punto.
        assertFalse(RucParaguayo.esValido("80012345-6"));
    }

    @Test
    void rechazaFormatosQueNoSonRuc() {
        assertFalse(RucParaguayo.esValido(null));
        assertFalse(RucParaguayo.esValido(""));
        assertFalse(RucParaguayo.esValido("80012345"));      // sin dígito verificador
        assertFalse(RucParaguayo.esValido("8001234-56"));    // dos dígitos después del guion
        assertFalse(RucParaguayo.esValido("sin-ruc"));
    }

    @Test
    void elMotivoDiceCualEsElDigitoQueCorresponde() {
        // El caso típico: el usuario tipea otro dígito verificador.
        assertEquals("el dígito verificador no coincide: para 80012345 corresponde -0, no -2",
                RucParaguayo.motivoInvalidez("80012345-2"));
    }

    @Test
    void elMotivoExplicaCadaTipoDeErrorDeFormato() {
        assertEquals("está vacío", RucParaguayo.motivoInvalidez("  "));
        assertEquals("le falta el guion con el dígito verificador (el formato es 80012345-0)",
                RucParaguayo.motivoInvalidez("80012345"));
        assertEquals("tiene más de un guion (el formato es 80012345-0)",
                RucParaguayo.motivoInvalidez("8001-2345-0"));
        assertEquals("no tiene número antes del guion", RucParaguayo.motivoInvalidez("-0"));
        assertEquals("después del guion tiene que ir un solo dígito (de 0 a 9)",
                RucParaguayo.motivoInvalidez("80012345-06"));
        assertEquals("después del guion tiene que ir un solo dígito (de 0 a 9)",
                RucParaguayo.motivoInvalidez("80012345-X"));
    }

    @Test
    void unRucValidoNoTieneMotivoDeInvalidez() {
        assertNull(RucParaguayo.motivoInvalidez("80012345-0"));
    }

    @Test
    void normalizaPuntosYEspacios() {
        assertEquals("80012345-0", RucParaguayo.normalizar(" 80.012.345-0 "));
        assertTrue(RucParaguayo.esValido(" 80.012.345-0 "));
    }

    @Test
    void separaNumeroYDigitoVerificador() {
        assertEquals("80012345", RucParaguayo.numero("80012345-0"));
        assertEquals(0, RucParaguayo.dv("80012345-0"));
    }
}
