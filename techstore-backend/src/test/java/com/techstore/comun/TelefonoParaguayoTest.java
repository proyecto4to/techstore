package com.techstore.comun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TelefonoParaguayoTest {

    @Test
    void normalizaFormatosLocalesEInternacionales() {
        assertEquals("+595981123456", TelefonoParaguayo.normalizar("0981 123 456"));
        assertEquals("+595981123456", TelefonoParaguayo.normalizar("981-123-456"));
        assertEquals("+595981123456", TelefonoParaguayo.normalizar("+595 981 123 456"));
        assertEquals("+595981123456", TelefonoParaguayo.normalizar("00595 981 123 456"));
    }

    @Test
    void rechazaValoresQueNoFormanUnNumero() {
        assertNull(TelefonoParaguayo.normalizar(null));
        assertNull(TelefonoParaguayo.normalizar("teléfono"));
        assertNull(TelefonoParaguayo.normalizar("123"));
    }
}

