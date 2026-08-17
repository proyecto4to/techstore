package com.techstore.compras;

import jakarta.validation.constraints.NotNull;

/**
 * Por dónde mandarle el pedido al proveedor.
 *
 * @param canal Correo, WhatsApp o Ambos. Sin valor por defecto a propósito: el
 *              canal lo elige quien compra, y elegirlo por él sería mandar un
 *              mensaje que no pidió.
 */
public record EnviarPedidoRequest(
        @NotNull(message = "{validacion.canal.obligatorio}")
        CanalEnvio canal) {
}
