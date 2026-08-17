package com.techstore.catalogo;

import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.PedidoDetalleRepository;
import com.techstore.ventas.TipoPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReposicionServiceTest {

    @Mock
    private PedidoDetalleRepository detalleRepository;

    private ReposicionService servicio;
    private Producto producto;

    @BeforeEach
    void preparar() {
        servicio = new ReposicionService(detalleRepository);
        ReflectionTestUtils.setField(servicio, "diasSeguridad", 7);
        ReflectionTestUtils.setField(servicio, "diasHistorial", 90);
        ReflectionTestUtils.setField(servicio, "diasEntregaPorDefecto", 15.0);

        producto = new Producto();
        producto.setId(10L);
        producto.setNombre("Producto reciente");
        producto.setStockMinimo(0);
        when(detalleRepository.unidadesVendidasDesde(
                eq(TipoPedido.Venta),
                eq(List.of(EstadoPedido.G, EstadoPedido.R)),
                any(LocalDateTime.class)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 100L}));
        when(detalleRepository.diasDeEntregaPorProducto())
                .thenReturn(List.<Object[]>of(new Object[]{10L, 1.0}));
    }

    @Test
    void unProductoNuevoUsaSoloLosDiasEnQuePudoVenderse() {
        producto.setCreatedAt(LocalDateTime.now().minusDays(9));

        PuntoDeReposicion punto = servicio.calcular(producto);

        assertEquals(10.0, punto.demandaDiaria(), 0.01);
        assertEquals(80, punto.unidades());
    }

    @Test
    void unProductoAntiguoUsaLaVentanaCompleta() {
        producto.setCreatedAt(LocalDateTime.now().minusDays(120));

        PuntoDeReposicion punto = servicio.calcular(producto);

        assertEquals(100.0 / 90.0, punto.demandaDiaria(), 0.01);
        assertEquals(9, punto.unidades());
    }
}
