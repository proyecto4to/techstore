package com.techstore.service;

import com.techstore.dto.request.ProductoRequest;
import com.techstore.dto.response.ProductoResponse;
import com.techstore.entity.Marca;
import com.techstore.entity.Modelo;
import com.techstore.entity.Producto;
import com.techstore.exception.BusinessException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.mapper.ProductoMapper;
import com.techstore.mapper.ProductoMapperImpl;
import com.techstore.repository.MarcaRepository;
import com.techstore.repository.ModeloRepository;
import com.techstore.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private MarcaRepository marcaRepository;

    @Mock
    private ModeloRepository modeloRepository;

    private final ProductoMapper mapper = new ProductoMapperImpl();

    private ProductoService productoService;

    @BeforeEach
    void setUp() {
        productoService = new ProductoService(productoRepository, marcaRepository, modeloRepository, mapper);
    }

    private ProductoRequest requestBase(Long marcaId, Long modeloId) {
        return new ProductoRequest(
                "Intel Core i9-13900K", "Procesador de 24 núcleos",
                new BigDecimal("2500000.00"), 10, 5, marcaId, modeloId,
                "/img/productos/cpu.svg", null);
    }

    @Test
    void crearProductoCalculaStockDisponible() {
        when(marcaRepository.findById(1L)).thenReturn(Optional.of(marca(1L, "Intel")));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse response = productoService.crear(requestBase(1L, null));

        assertEquals("Intel Core i9-13900K", response.nombre());
        assertEquals(5, response.stockReal());
        assertEquals(5, response.stockDisponible());
        assertEquals(Boolean.TRUE, response.activo());
        assertEquals("Intel", response.marca().nombre());
    }

    @Test
    void crearConMarcaInexistenteFalla() {
        when(marcaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productoService.crear(requestBase(99L, null)));
    }

    @Test
    void crearConModeloDeOtraMarcaFalla() {
        when(marcaRepository.findById(1L)).thenReturn(Optional.of(marca(1L, "Intel")));
        Modelo modeloAjeno = new Modelo();
        modeloAjeno.setId(5L);
        modeloAjeno.setNombre("RTX 4090");
        modeloAjeno.setMarca(marca(2L, "NVIDIA"));
        when(modeloRepository.findById(5L)).thenReturn(Optional.of(modeloAjeno));

        assertThrows(BusinessException.class, () -> productoService.crear(requestBase(1L, 5L)));
    }

    @Test
    void eliminarHaceBajaLogica() {
        Producto producto = new Producto();
        producto.setId(3L);
        producto.setNombre("SSD");
        producto.setActivo(true);
        when(productoRepository.findById(3L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        productoService.eliminar(3L);

        assertFalse(producto.getActivo());
        verify(productoRepository).save(producto);
    }

    @Test
    void obtenerInexistenteFalla() {
        when(productoRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productoService.obtener(42L));
    }

    private Marca marca(Long id, String nombre) {
        Marca marca = new Marca();
        marca.setId(id);
        marca.setNombre(nombre);
        return marca;
    }
}
