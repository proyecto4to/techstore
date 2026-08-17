package com.techstore.notificaciones.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstore.comun.tenant.TenantContext;
import com.techstore.facturacion.Factura;
import com.techstore.usuarios.Usuario;
import com.techstore.ventas.Pedido;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxPublisherTest {
    @Mock private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearTenant() {
        TenantContext.limpiar();
    }

    @Test
    void pedidoCreatesOneIdempotentJobPerChannelWithoutPii() {
        TenantContext.fijar(7L);
        NotificationOutboxPublisher publisher = new NotificationOutboxPublisher(jdbcTemplate, new ObjectMapper());
        Usuario user = new Usuario();
        user.setId(12L);
        user.setEmail("must-not-be-in-payload@techstore.py");
        Pedido pedido = new Pedido();
        pedido.setId(81L);
        pedido.setUsuario(user);

        publisher.pedidoCreado(pedido);

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(3)).update(contains("ON CONFLICT"), arguments.capture());
        List<Object[]> calls = arguments.getAllValues();
        assertEquals(List.of("EMAIL", "INTERNAL", "PUSH"),
                calls.stream().map(values -> values[2]).sorted().toList());
        for (Object[] values : calls) {
            assertEquals(7L, values[0]);
            assertEquals("PEDIDO_CREADO", values[1]);
            assertTrue(values[6].toString().contains("\"pedidoId\":81"));
            assertFalse(values[6].toString().contains("techstore.py"));
            assertTrue(values[7].toString().endsWith(":" + values[2]));
        }
    }

    @Test
    void directSaleWithoutRecipientDoesNotCreateDeliveryJobs() {
        NotificationOutboxPublisher publisher = new NotificationOutboxPublisher(jdbcTemplate, new ObjectMapper());
        Usuario cashier = new Usuario();
        cashier.setId(5L);
        Pedido pedido = new Pedido();
        pedido.setId(10L);
        pedido.setUsuario(cashier);
        Factura factura = new Factura();
        factura.setId(21L);
        factura.setPedido(pedido);

        publisher.pagoConfirmado(factura, false);

        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }
}
