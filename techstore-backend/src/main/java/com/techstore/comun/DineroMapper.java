package com.techstore.comun;

import java.math.BigDecimal;

/**
 * Traduce entre el {@link Dinero} del dominio y el número pelado que viaja por
 * la API. La heredan los mappers de MapStruct, que sin esto no saben convertir.
 *
 * <p>Se hereda en vez de inyectarse con {@code uses}: así los métodos quedan en
 * el propio mapper generado y una instancia hecha a mano —como la de los tests—
 * funciona sin tener que completarle dependencias.
 *
 * <p>Hacia afuera el importe sale como número y la moneda viaja aparte, en un
 * campo propio de la respuesta. Es a propósito: si el importe saliera como
 * objeto, el panel —que hace cuentas con esos valores— tendría que cambiar en
 * cada pantalla el mismo día que se toca el backend.
 */
public interface DineroMapper {

    default BigDecimal aMonto(Dinero importe) {
        return importe == null ? null : importe.monto();
    }

    /**
     * Los pedidos entran sin moneda, así que se asume la del sistema. Este es el
     * único lugar donde eso se decide: el día que el request traiga su propia
     * moneda, se cambia acá y nada más.
     */
    default Dinero aDinero(BigDecimal monto) {
        return monto == null ? null : Dinero.de(monto, Moneda.porDefecto());
    }
}
