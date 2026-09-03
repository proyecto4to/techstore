package com.techstore.comun;

import com.techstore.mensajeria.CrearConversacionRequest;
import com.techstore.ventas.CheckoutRequest;
import com.techstore.ventas.CheckoutRequest.ItemCarrito;
import com.techstore.ventas.MetodoEntrega;
import com.techstore.ventas.MetodoPagoCheckout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GeneXusCompatibilityRequestTest {

    @Test
    void checkoutInterpretaIdsOpcionalesEnCeroComoAusentes() {
        CheckoutRequest request = new CheckoutRequest(
                List.of(new ItemCarrito(6L, 1)),
                "Retiro en tienda",
                0L,
                MetodoEntrega.RETIRO_TIENDA,
                MetodoPagoCheckout.PAGO_EN_LOCAL,
                0L);

        assertNull(request.direccionEntregaId());
        assertNull(request.tarifaEnvioId());
    }

    @Test
    void conversacionInterpretaPedidoEnCeroComoAusente() {
        CrearConversacionRequest request = new CrearConversacionRequest(
                0L, "gx-message-1", "Consulta sin pedido");

        assertNull(request.pedidoId());
    }

    @Test
    void filtrosInterpretanIdsNoPositivosComoAusentes() {
        assertNull(FiltroOpcional.id(null));
        assertNull(FiltroOpcional.id(0L));
        assertNull(FiltroOpcional.id(-1L));
        assertEquals(7L, FiltroOpcional.id(7L));
    }
}
