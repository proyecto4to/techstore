package com.techstore.catalogo;

import com.techstore.comun.ResourceNotFoundException;
import com.techstore.promociones.CalculadorDePrecios;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoritoService {

    private final FavoritoRepository favoritoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoService productoService;
    private final ProductoMapper productoMapper;
    private final CalculadorDePrecios calculadorDePrecios;

    @Transactional(readOnly = true)
    public List<FavoritoResponse> listar(String email) {
        return favoritoRepository.listarVisibles(email).stream()
                .map(this::respuesta)
                .toList();
    }

    @Transactional
    public FavoritoResponse agregar(String email, Long productoId) {
        Usuario usuario = usuario(email);
        Producto producto = productoService.buscarProductoPublico(productoId);
        return favoritoRepository.findByUsuarioIdAndProductoId(usuario.getId(), productoId)
                .map(this::respuesta)
                .orElseGet(() -> {
                    Favorito favorito = new Favorito();
                    favorito.setTenantId(usuario.getTenantId());
                    favorito.setUsuario(usuario);
                    favorito.setProducto(producto);
                    return respuesta(favoritoRepository.save(favorito));
                });
    }

    @Transactional
    public void quitar(String email, Long productoId) {
        Usuario usuario = usuario(email);
        favoritoRepository.deleteByUsuarioIdAndProductoId(usuario.getId(), productoId);
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("usuarios.no.encontrado", email));
    }

    private FavoritoResponse respuesta(Favorito favorito) {
        // Los favoritos son la lista donde más se mira el precio: si acá
        // saliera el de lista, la promoción recién aparecería en el carrito.
        return new FavoritoResponse(
                productoMapper.toPublicResponse(favorito.getProducto())
                        .con(calculadorDePrecios.precioDe(favorito.getProducto())),
                favorito.getCreadoEn());
    }
}
