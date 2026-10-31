package com.techstore.ventas;

import com.techstore.usuarios.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Anota los pasos de un pedido en su línea de tiempo.
 *
 * <p>Se llama dentro de la transacción del cambio que cuenta: si el cambio se
 * deshace, la entrada también.
 */
@Service
@RequiredArgsConstructor
public class PedidoHistorial {

    private final PedidoEventoRepository repository;

    public void registrar(Pedido pedido, TipoEventoPedido tipo, String detalle, Usuario usuario) {
        PedidoEvento evento = new PedidoEvento();
        evento.setPedido(pedido);
        evento.setTipo(tipo);
        evento.setDetalle(detalle == null || detalle.length() <= 500 ? detalle : detalle.substring(0, 500));
        evento.setUsuario(usuario);
        repository.save(evento);
    }
}
