package com.techstore.empresa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Puntos de expedición")
class PuntoExpedicionTest {

    private static PuntoExpedicion punto(String establecimiento, String nro, boolean porDefecto) {
        PuntoExpedicion p = new PuntoExpedicion();
        p.setEstablecimiento(establecimiento);
        p.setPunto(nro);
        p.setPorDefecto(porDefecto);
        return p;
    }

    @Test
    @DisplayName("el prefijo es el que sale impreso en el documento")
    void elPrefijo() {
        assertEquals("001-002", punto("001", "002", false).prefijo());
    }

    @Test
    @DisplayName("el punto por defecto conserva la serie histórica")
    void elPorDefectoUsaElNumeradorDeSiempre() {
        // Sin sufijo a propósito: es la entidad que ya venía contando. Ponerle
        // uno reiniciaría la numeración en 1 sobre un timbrado en uso, que es
        // un problema fiscal, no un detalle.
        assertEquals("FACTURA", punto("001", "001", true).autonumerador("FACTURA"));
    }

    @Test
    @DisplayName("cada punto nuevo lleva su propia serie")
    void cadaPuntoCuentaAparte() {
        // 001-001-0000001 y 001-002-0000001 son dos documentos distintos y los
        // dos válidos: la numeración es independiente por punto.
        assertEquals("FACTURA-001-002", punto("001", "002", false).autonumerador("FACTURA"));
        assertEquals("NOTACREDITO-002-001", punto("002", "001", false).autonumerador("NOTACREDITO"));
    }
}
