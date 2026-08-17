package com.techstore.comun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumeroComprobanteTest {

    @Test
    void aceptaElFormatoCompleto() {
        assertTrue(NumeroComprobante.esValido("001-001-0000250"));
    }

    @Test
    void rechazaLoQueNoTengaLasTresPartesConSuLargo() {
        assertFalse(NumeroComprobante.esValido("1-1-250"));
        assertFalse(NumeroComprobante.esValido("001-001-250"));
        assertFalse(NumeroComprobante.esValido("0010010000250"));
        assertFalse(NumeroComprobante.esValido("001-001-00002500"));
        assertFalse(NumeroComprobante.esValido(null));
    }

    @Test
    void completaConCerosLoQueSeTipeaCorto() {
        // Es lo que pasa cuando alguien copia el número de un papel: nadie
        // escribe los ceros de adelante.
        assertEquals("001-001-0000250", NumeroComprobante.normalizar("1-1-250"));
        assertEquals("001-001-0000250", NumeroComprobante.normalizar("001-001-250"));
        assertEquals("002-015-0001240", NumeroComprobante.normalizar("2-15-1240"));
    }

    @Test
    void loQueYaEstaCompletoNoCambia() {
        assertEquals("001-002-0000250", NumeroComprobante.normalizar("001-002-0000250"));
    }

    @Test
    void recortaEspaciosAlrededor() {
        assertEquals("001-001-0000250", NumeroComprobante.normalizar("  1-1-250  "));
    }

    @Test
    void loQueNoTieneTresPartesVuelveTalCualParaQueLoRechaceLaValidacion() {
        // Deformarlo acá escondería el error: mejor que llegue entero a quien
        // tiene que decir qué se esperaba.
        assertEquals("001-0000250", NumeroComprobante.normalizar("001-0000250"));
        assertEquals("A-1-250", NumeroComprobante.normalizar("A-1-250"));
        assertFalse(NumeroComprobante.esValido(NumeroComprobante.normalizar("A-1-250")));
    }

    @Test
    void vacioEsNulo() {
        assertNull(NumeroComprobante.normalizar(""));
        assertNull(NumeroComprobante.normalizar("   "));
        assertNull(NumeroComprobante.normalizar(null));
    }
}
