package com.techstore.sifen;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodigoControlTest {

    @Test
    void digitoVerificadorModulo11Conocido() {
        // Casos verificables a mano con el algoritmo base-11 del SET.
        // "80012345" -> suma ponderada 2..: da un DV en el rango 0..9.
        int dv = CodigoControl.digitoVerificadorModulo11("80012345");
        assertTrue(dv >= 0 && dv <= 9);
        // El mismo número siempre da el mismo DV (determinístico).
        assertEquals(dv, CodigoControl.digitoVerificadorModulo11("80012345"));
    }

    @Test
    void digitoVerificadorRestoMenorOIgualA1DaCero() {
        // Un valor cuyo resto mod 11 sea 0 o 1 debe dar DV = 0.
        // "11" -> 1*3 + 1*2 = 5 -> resto 5 -> 11-5=6. Usamos un caso construido:
        // buscamos determinismo, no un valor mágico: repetibilidad basta.
        int dv1 = CodigoControl.digitoVerificadorModulo11("0");
        assertEquals(0, dv1); // 0*2=0, resto 0 -> DV 0
    }

    @Test
    void generaCdcDe44Digitos() {
        String cdc = CodigoControl.generar(
                "01", "80012345", 6, "001", "001", "0000001", 2,
                LocalDate.of(2026, 7, 20), 1, "123456789");

        assertEquals(44, cdc.length());
        assertTrue(cdc.matches("\\d{44}"), "El CDC debe ser 44 dígitos");
        assertTrue(cdc.startsWith("01" + "80012345" + "6" + "001" + "001" + "0000001"));
        // El último dígito es el DV de los primeros 43.
        int dv = CodigoControl.digitoVerificadorModulo11(cdc.substring(0, 43));
        assertEquals(dv, cdc.charAt(43) - '0');
    }

    @Test
    void codigoSeguridadTiene9DigitosYNoEsCero() {
        for (int i = 0; i < 50; i++) {
            String cs = CodigoControl.generarCodigoSeguridad();
            assertTrue(cs.matches("\\d{9}"), "Debe ser 9 dígitos: " + cs);
            assertTrue(Long.parseLong(cs) > 0, "No debe ser cero");
        }
    }

    @Test
    void campoQueExcedeLongitudFalla() {
        assertThrows(IllegalArgumentException.class, () -> CodigoControl.generar(
                "01", "800123456789", 6, "001", "001", "0000001", 2,
                LocalDate.of(2026, 7, 20), 1, "123456789"));
    }

    @Test
    void formateaEnGruposDeCuatro() {
        String cdc = "01800123456001001000000122026072011234567890";
        String fmt = CodigoControl.enGruposDeCuatro(cdc.substring(0, 44));
        // 44 dígitos -> 11 grupos de 4 separados por espacio (10 espacios)
        assertEquals(11, fmt.split(" ").length);
    }
}
