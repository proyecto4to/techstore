package com.techstore.service;

import com.techstore.dto.request.ProductoRequest;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.ProductoResponse;
import com.techstore.entity.Marca;
import com.techstore.entity.Modelo;
import com.techstore.entity.Producto;
import com.techstore.exception.BusinessException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.mapper.ProductoMapper;
import com.techstore.repository.MarcaRepository;
import com.techstore.repository.ModeloRepository;
import com.techstore.repository.ProductoRepository;
import com.techstore.repository.ProductoSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final MarcaRepository marcaRepository;
    private final ModeloRepository modeloRepository;
    private final ProductoMapper productoMapper;

    @Transactional(readOnly = true)
    public PageResponse<ProductoResponse> listar(String search, Long marcaId, boolean incluirInactivos, Pageable pageable) {
        return PageResponse.from(
                productoRepository.findAll(ProductoSpecs.conFiltros(search, marcaId, incluirInactivos), pageable)
                        .map(productoMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return productoMapper.toResponse(buscarProducto(id));
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        Producto producto = productoMapper.toEntity(request);
        if (producto.getActivo() == null) {
            producto.setActivo(true);
        }
        aplicarRelaciones(producto, request);
        return productoMapper.toResponse(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest request) {
        Producto producto = buscarProducto(id);
        productoMapper.updateEntity(request, producto);
        aplicarRelaciones(producto, request);
        return productoMapper.toResponse(productoRepository.save(producto));
    }

    /**
     * Baja lógica: el producto deja de aparecer en el catálogo pero se conserva
     * para mantener la integridad de pedidos y facturas históricas.
     */
    @Transactional
    public void eliminar(Long id) {
        Producto producto = buscarProducto(id);
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    private Producto buscarProducto(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    private void aplicarRelaciones(Producto producto, ProductoRequest request) {
        Marca marca = null;
        if (request.marcaId() != null) {
            marca = marcaRepository.findById(request.marcaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Marca", request.marcaId()));
        }
        producto.setMarca(marca);

        Modelo modelo = null;
        if (request.modeloId() != null) {
            modelo = modeloRepository.findById(request.modeloId())
                    .orElseThrow(() -> new ResourceNotFoundException("Modelo", request.modeloId()));
            if (marca != null && !modelo.getMarca().getId().equals(marca.getId())) {
                throw new BusinessException("El modelo indicado no pertenece a la marca seleccionada");
            }
        }
        producto.setModelo(modelo);
    }
}
