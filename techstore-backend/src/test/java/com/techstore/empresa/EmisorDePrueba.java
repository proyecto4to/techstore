package com.techstore.empresa;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Datos del emisor fijos para los tests, sin base de datos de por medio.
 *
 * <p>Vive acá y no adentro de una clase de test porque lo necesitan varios
 * paquetes: la factura, la nota de crédito, el pedido a proveedor y el alta de
 * usuarios arman correos o documentos y todos precisan un emisor.
 */
public final class EmisorDePrueba {

    private EmisorDePrueba() {
    }

    /**
     * Una configuración de empresa que siempre devuelve este emisor. Los stubs
     * son lenient porque hay tests que fallan antes de llegar a necesitarlos.
     */
    public static EmpresaConfigService config(EmpresaProperties emisor) {
        EmpresaConfigService config = mock(EmpresaConfigService.class);
        lenient().when(config.actual()).thenReturn(emisor);
        // La emisión pide los datos por este otro método, que además exige que
        // estén completos. Acá se da por bueno el emisor que arma el test: lo
        // que se prueba es el documento, no la validación, que tiene el suyo.
        lenient().when(config.actualParaFacturar()).thenReturn(emisor);
        return config;
    }
}
