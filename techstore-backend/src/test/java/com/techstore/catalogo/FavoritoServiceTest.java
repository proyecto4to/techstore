package com.techstore.catalogo;

import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import com.techstore.promociones.PromocionesDePrueba;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoritoServiceTest {

    @Mock
    private FavoritoRepository favoritoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProductoService productoService;

    private FavoritoService service;
    private final ProductoMapper mapper = new ProductoMapperImpl();

    @BeforeEach
    void setUp() {
        service = new FavoritoService(
                favoritoRepository, usuarioRepository, productoService, mapper, PromocionesDePrueba.sinNinguna());
    }

    @Test
    void agregarEsIdempotenteParaElMismoUsuarioYProducto() {
        Usuario usuario = new Usuario();
        usuario.setId(3L);
        usuario.setTenantId(1L);
        Producto producto = producto();
        Favorito existente = new Favorito();
        existente.setUsuario(usuario);
        existente.setProducto(producto);

        when(usuarioRepository.findByEmail("cliente@techstore.py")).thenReturn(Optional.of(usuario));
        when(productoService.buscarProductoPublico(8L)).thenReturn(producto);
        when(favoritoRepository.findByUsuarioIdAndProductoId(3L, 8L)).thenReturn(Optional.of(existente));

        FavoritoResponse respuesta = service.agregar("cliente@techstore.py", 8L);

        assertEquals(8L, respuesta.producto().id());
        verify(favoritoRepository, never()).save(any());
    }

    @Test
    void quitarSeLimitaAlUsuarioAutenticado() {
        Usuario usuario = new Usuario();
        usuario.setId(4L);
        when(usuarioRepository.findByEmail("otro@techstore.py")).thenReturn(Optional.of(usuario));

        service.quitar("otro@techstore.py", 8L);

        verify(favoritoRepository).deleteByUsuarioIdAndProductoId(4L, 8L);
    }

    private Producto producto() {
        Producto producto = new Producto();
        producto.setId(8L);
        producto.setNombre("Monitor");
        producto.setPrecio(com.techstore.comun.Dinero.de(1_000_000, com.techstore.comun.Moneda.PYG));
        producto.setStockReal(2);
        producto.setStockComprometido(0);
        producto.setActivo(true);
        return producto;
    }
}
