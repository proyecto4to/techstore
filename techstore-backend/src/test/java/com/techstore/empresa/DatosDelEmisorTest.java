package com.techstore.empresa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Datos obligatorios del emisor")
class DatosDelEmisorTest {

    /** Un emisor con todo cargado, como el que trae application.properties. */
    private static EmpresaProperties completo() {
        return new EmpresaProperties(
                "TechStore Paraguay", "80012345-6", "Avda. España 1234", "Asunción",
                "(021) 555-123", "facturacion@techstore.py", "Venta de equipos", "12345678",
                "01/01/2026 al 31/12/2026",
                2, "001", "001", "47411", "11", "CENTRAL", "1", "ASUNCION", "1", "ASUNCION", "2026-01-01", "0");
    }

    @Test
    @DisplayName("con todo cargado no falta nada")
    void emisorCompleto() {
        assertEquals(List.of(), DatosDelEmisor.faltantes(completo()));
        assertFalse(DatosDelEmisor.faltaTipoContribuyente(completo()));
    }

    @Test
    @DisplayName("detecta el timbrado sin cargar")
    void faltaElTimbrado() {
        EmpresaProperties sinTimbrado = new EmpresaProperties(
                "TechStore Paraguay", "80012345-6", "Avda. España 1234", "Asunción",
                "(021) 555-123", "facturacion@techstore.py", "Venta de equipos", null,
                "01/01/2026 al 31/12/2026",
                2, "001", "001", "47411", "11", "CENTRAL", "1", "ASUNCION", "1", "ASUNCION", "2026-01-01", "0");

        assertEquals(List.of("Número de timbrado"), DatosDelEmisor.faltantes(sinTimbrado));
    }

    @Test
    @DisplayName("una cadena en blanco cuenta como faltante")
    void elBlancoNoEsUnDato() {
        // "   " pasaría cualquier chequeo de nulo y saldría impreso como un hueco.
        EmpresaProperties enBlanco = new EmpresaProperties(
                "TechStore Paraguay", "80012345-6", "   ", "Asunción",
                "(021) 555-123", "facturacion@techstore.py", "Venta de equipos", "12345678",
                "01/01/2026 al 31/12/2026",
                2, "001", "001", "47411", "11", "CENTRAL", "1", "ASUNCION", "1", "ASUNCION", "2026-01-01", "0");

        assertTrue(DatosDelEmisor.faltantes(enBlanco).contains("Dirección"));
    }

    @Test
    @DisplayName("los códigos que valida el SIFEN también son obligatorios")
    void faltanLosCodigosDelSifen() {
        // Sin estos el XML se arma igual, pero el documento no es válido: es el
        // peor momento para enterarse, con el timbrado ya consumido.
        EmpresaProperties sinCodigos = new EmpresaProperties(
                "TechStore Paraguay", "80012345-6", "Avda. España 1234", "Asunción",
                "(021) 555-123", "facturacion@techstore.py", "Venta de equipos", "12345678",
                "01/01/2026 al 31/12/2026",
                2, "001", "001", null, null, null, null, null, null, null, "2026-01-01", "0");

        assertEquals(
                List.of("Código de actividad económica", "Código de departamento", "Departamento",
                        "Código de distrito", "Distrito", "Código de ciudad", "Ciudad (SIFEN)"),
                DatosDelEmisor.faltantes(sinCodigos));
    }

    @Test
    @DisplayName("el tipo de contribuyente se comprueba aparte, porque es numérico")
    void faltaElTipoDeContribuyente() {
        EmpresaProperties sinTipo = new EmpresaProperties(
                "TechStore Paraguay", "80012345-6", "Avda. España 1234", "Asunción",
                "(021) 555-123", "facturacion@techstore.py", "Venta de equipos", "12345678",
                "01/01/2026 al 31/12/2026",
                null, "001", "001", "47411", "11", "CENTRAL", "1", "ASUNCION", "1", "ASUNCION", "2026-01-01", "0");

        assertTrue(DatosDelEmisor.faltaTipoContribuyente(sinTipo));
        // Y no aparece en la lista de texto: se informa por separado.
        assertEquals(List.of(), DatosDelEmisor.faltantes(sinTipo));
    }

    @Test
    @DisplayName("reconoce los valores fiscales distribuidos solo para demostración")
    void reconoceEmisorDemo() {
        assertTrue(DatosDelEmisor.contieneDatosDemo(completo()));

        EmpresaProperties real = new EmpresaProperties(
                "Comercial Real SA", "80199999-0", "Avda. Real 100", "Asunción",
                "021600000", "facturas@comercial.example", "Comercio", "98765432",
                "01/01/2026 al 31/12/2026",
                2, "001", "001", "47411", "11", "CENTRAL", "1", "ASUNCION", "1", "ASUNCION", "2026-01-01", "0");

        assertFalse(DatosDelEmisor.contieneDatosDemo(real));
    }
}
