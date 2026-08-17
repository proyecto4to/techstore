package com.techstore.compras;

import com.techstore.notificaciones.EstadoCorreo;
import com.techstore.ventas.Pedido;

import java.time.LocalDateTime;

/**
 * Qué pasó al mandarle el pedido al proveedor.
 *
 * <p>{@code enviado} distingue lo que salió de verdad de lo que quedó
 * registrado nada más. Sin SMTP configurado el correo se guarda en la bandeja
 * de salida pero no viaja, y el usuario tiene que saberlo: si cree que el
 * proveedor lo recibió, se queda esperando una mercadería que nadie pidió.
 */
public record EnvioPedidoResponse(
        Long compraId,
        Integer numero,
        String canal,
        String destinatario,
        boolean enviado,
        String resultado,
        String detalle,
        LocalDateTime enviadoEn) {

    static EnvioPedidoResponse de(Pedido compra, CanalEnvio canal, String destino, EstadoCorreo estado) {
        boolean salio = estado == EstadoCorreo.ENVIADO;
        return new EnvioPedidoResponse(
                compra.getId(),
                compra.getNumero(),
                canal.etiqueta(),
                destino,
                salio,
                estado.name(),
                explicar(estado, destino),
                compra.getEnviadoEn());
    }

    private static String explicar(EstadoCorreo estado, String destino) {
        return switch (estado) {
            case ENVIADO -> "El pedido salió a " + destino + ".";
            case SIMULADO -> "El pedido quedó en la bandeja de salida pero NO se envió: "
                    + "no hay un servidor de correo configurado. Cargalo en Admin → Correos.";
            case ERROR -> "El servidor de correo rechazó el envío. Mirá el detalle en Admin → Correos.";
            case PENDIENTE -> "El envío quedó pendiente.";
        };
    }
}
