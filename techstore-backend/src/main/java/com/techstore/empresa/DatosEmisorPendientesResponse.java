package com.techstore.empresa;

import java.util.List;

/**
 * Qué le falta al emisor para poder facturar. El panel lo consulta para avisar
 * antes de que alguien intente vender, en vez de que el aviso llegue como un
 * error en medio de un cobro.
 *
 * @param completo   si se puede facturar
 * @param faltantes  nombres para mostrar ("Número de timbrado")
 * @param campos     nombres internos, para marcar los inputs del formulario
 */
public record DatosEmisorPendientesResponse(
        boolean completo,
        List<String> faltantes,
        List<String> campos) {
}
