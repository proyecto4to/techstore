package com.techstore.auditoria;

import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cómo quedan escritos los valores de un cambio auditado.
 *
 * <p>Lo que se mira acá es que un importe salga legible. La pantalla de
 * auditoría mostraba «Gs. 4700000» porque el mapa guardaba el
 * {@code toString()} de {@link Dinero}, que devuelve el número plano —está bien
 * así para los registros técnicos y para el XML del SIFEN, pero no para algo
 * que va a leer una persona—.
 */
@DisplayName("Valores de un cambio auditado")
class CambiosAuditadosTest {

    @Test
    void unImporteSeGuardaConSeparadorDeMiles() {
        AuditoriaService.Cambios cambios = AuditoriaService.Cambios.nuevo()
                .valor("efectivoTeorico", Dinero.de(4_700_000L, Moneda.PYG));

        assertEquals("Gs. 4.700.000", cambios.mapa().get("efectivoTeorico"));
    }

    @Test
    void elAntesYElDespuesTambien() {
        AuditoriaService.Cambios cambios = AuditoriaService.Cambios.nuevo()
                .de("saldo", Dinero.de(500_000L, Moneda.PYG), Dinero.de(1_250_000L, Moneda.PYG));

        @SuppressWarnings("unchecked")
        Map<String, Object> par = (Map<String, Object>) cambios.mapa().get("saldo");
        assertEquals("Gs. 500.000", par.get("antes"));
        assertEquals("Gs. 1.250.000", par.get("despues"));
    }

    @Test
    void loQueNoEsUnImporteSigueSaliendoIgual() {
        AuditoriaService.Cambios cambios = AuditoriaService.Cambios.nuevo()
                .valor("numero", 16)
                .valor("estado", "CERRADA")
                .valor("nada", null);

        assertEquals("16", cambios.mapa().get("numero"));
        assertEquals("CERRADA", cambios.mapa().get("estado"));
        assertTrue(cambios.mapa().containsKey("nada"));
    }

    @Test
    void dosImportesIgualesNoSeRegistranComoCambio() {
        // Un registro lleno de campos idénticos esconde el que importa, y la
        // comparación se hace sobre el texto ya formateado.
        AuditoriaService.Cambios cambios = AuditoriaService.Cambios.nuevo()
                .de("saldo", Dinero.de(500_000L, Moneda.PYG), Dinero.de(500_000L, Moneda.PYG));

        assertTrue(cambios.vacio());
    }
}
