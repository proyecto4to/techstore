package com.techstore.ventas;

import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoMapper;
import com.techstore.catalogo.ProductoMapperImpl;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    private CarritoService service;

    @BeforeEach
    void setUp() {
        ProductoMapper mapper = new ProductoMapperImpl();
        service = new CarritoService(productoRepository, mapper);
    }

    @Test
    void devuelvePrecioCanonicoYSubtotalSinExponerStock() {
        when(productoRepository.findAllById(any())).thenReturn(List.of(producto(7L, 5, true)));

        var respuesta = service.validar(new ValidarCarritoRequest(List.of(
                new ValidarCarritoRequest.Item(7L, 2))));

        assertTrue(respuesta.valido());
        assertEquals(EstadoLineaCarrito.DISPONIBLE, respuesta.items().getFirst().estado());
        assertEquals(new BigDecimal("3000000"), respuesta.items().getFirst().subtotal());
        assertEquals(new BigDecimal("3000000"), respuesta.totales().get("PYG"));
    }

    @Test
    void informaCantidadInsuficienteSinRevelarElStockExacto() {
        when(productoRepository.findAllById(any())).thenReturn(List.of(producto(7L, 1, true)));

        var respuesta = service.validar(new ValidarCarritoRequest(List.of(
                new ValidarCarritoRequest.Item(7L, 2))));

        assertFalse(respuesta.valido());
        assertEquals(EstadoLineaCarrito.CANTIDAD_INSUFICIENTE, respuesta.items().getFirst().estado());
        assertNull(respuesta.items().getFirst().subtotal());
        assertTrue(respuesta.totales().isEmpty());
    }

    @Test
    void fusionaDuplicadosYRechazaLaCantidadAcumuladaExcesiva() {
        when(productoRepository.findAllById(any())).thenReturn(List.of(producto(7L, 200, true)));

        var respuesta = service.validar(new ValidarCarritoRequest(List.of(
                new ValidarCarritoRequest.Item(7L, 60),
                new ValidarCarritoRequest.Item(7L, 50))));

        assertFalse(respuesta.valido());
        assertEquals(1, respuesta.items().size());
        assertEquals(110, respuesta.items().getFirst().cantidad());
        assertEquals(EstadoLineaCarrito.CANTIDAD_EXCESIVA, respuesta.items().getFirst().estado());
    }

    @Test
    void productoInactivoNoFiltraSusDatos() {
        when(productoRepository.findAllById(any())).thenReturn(List.of(producto(7L, 5, false)));

        var respuesta = service.validar(new ValidarCarritoRequest(List.of(
                new ValidarCarritoRequest.Item(7L, 1))));

        assertEquals(EstadoLineaCarrito.NO_DISPONIBLE, respuesta.items().getFirst().estado());
        assertNull(respuesta.items().getFirst().producto());
    }

    private Producto producto(long id, int stock, boolean activo) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre("Notebook Titanium");
        producto.setPrecio(Dinero.de(1_500_000, Moneda.PYG));
        producto.setStockReal(stock);
        producto.setStockComprometido(0);
        producto.setActivo(activo);
        return producto;
    }
}
