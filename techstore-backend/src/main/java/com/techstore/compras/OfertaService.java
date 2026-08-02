package com.techstore.compras;

import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Carga y consulta manual del catálogo de un proveedor.
 *
 * <p>Lo normal es que estas ofertas se creen solas al recibir compras. Esto es
 * para lo otro: registrar que un proveedor tiene algo antes de haberle comprado
 * —una cotización por teléfono, una lista de precios— para poder compararlo
 * desde la primera vez.
 */
@Service
@RequiredArgsConstructor
public class OfertaService {

    private final ProveedorProductoRepository ofertaRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public List<OfertaResponse> listar(Long proveedorId) {
        exigirProveedor(proveedorId);
        return ofertaRepository.findByProveedorId(proveedorId).stream()
                .map(OfertaResponse::from)
                .toList();
    }

    /** Crea la oferta o pisa el precio de la que ya existía. */
    @Transactional
    public OfertaResponse guardar(Long proveedorId, OfertaRequest request) {
        Proveedor proveedor = exigirProveedor(proveedorId);
        Producto producto = productoRepository.findById(request.productoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "catalogo.producto.no.encontrado", request.productoId()));

        ProveedorProducto oferta = ofertaRepository
                .findByProveedorIdAndProductoId(proveedorId, request.productoId())
                .orElseGet(() -> {
                    ProveedorProducto nueva = new ProveedorProducto();
                    nueva.setProveedor(proveedor);
                    nueva.setProducto(producto);
                    return nueva;
                });

        // El precio se carga en la moneda del sistema: el request no la trae,
        // igual que en el resto de las pantallas.
        oferta.setMoneda(Moneda.porDefecto());
        oferta.setPrecio(Dinero.de(request.precio(), Moneda.porDefecto()));
        oferta.setDisponible(request.disponible() == null || request.disponible());
        oferta.setActualizadoEn(LocalDateTime.now());
        return OfertaResponse.from(ofertaRepository.save(oferta));
    }

    private Proveedor exigirProveedor(Long proveedorId) {
        return proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "compras.proveedor.no.encontrado", proveedorId));
    }
}
