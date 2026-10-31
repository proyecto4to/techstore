package com.techstore.ventas;

import jakarta.validation.constraints.Size;

/**
 * La aprobación de una transferencia.
 *
 * @param referencia el número de operación que muestra el banco, si quien
 *                   verifica lo quiere dejar anotado. Queda en el pago, y es lo
 *                   que se busca después en el extracto para conciliar.
 */
public record AprobacionPagoRequest(@Size(max = 50) String referencia) {
}
