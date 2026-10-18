package com.techstore.promociones;

import com.techstore.catalogo.MarcaRepository;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.BusinessException;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.comun.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Alta, edición y encendido de promociones.
 *
 * <p>Una promoción no se borra: se apaga. Los pedidos viejos guardan el
 * descuento que tuvieron en su propia línea, así que borrarla no cambiaría
 * ningún importe, pero sí dejaría sin explicación por qué un pedido de marzo
 * salió más barato que el mismo producto hoy.
 */
@Service
@RequiredArgsConstructor
public class PromocionService {

    private final PromocionRepository repository;
    private final ProductoRepository productoRepository;
    private final MarcaRepository marcaRepository;

    @Transactional(readOnly = true)
    public PageResponse<PromocionResponse> listar(Pageable pageable) {
        LocalDateTime momento = LocalDateTime.now();
        return PageResponse.from(repository.listar(pageable)
                .map(promocion -> PromocionResponse.de(promocion, momento)));
    }

    @Transactional(readOnly = true)
    public PromocionResponse obtener(Long id) {
        return PromocionResponse.de(buscar(id), LocalDateTime.now());
    }

    @Transactional
    public PromocionResponse crear(PromocionRequest request) {
        Promocion promocion = new Promocion();
        aplicar(request, promocion);
        promocion.setActiva(request.activa() == null || request.activa());
        return PromocionResponse.de(repository.save(promocion), LocalDateTime.now());
    }

    @Transactional
    public PromocionResponse editar(Long id, PromocionRequest request) {
        Promocion promocion = buscar(id);
        aplicar(request, promocion);
        if (request.activa() != null) {
            promocion.setActiva(request.activa());
        }
        return PromocionResponse.de(repository.save(promocion), LocalDateTime.now());
    }

    /**
     * El interruptor que se usa de verdad: encender y apagar sin editar nada.
     */
    @Transactional
    public PromocionResponse cambiarEstado(Long id, boolean activa) {
        Promocion promocion = buscar(id);
        promocion.setActiva(activa);
        return PromocionResponse.de(repository.save(promocion), LocalDateTime.now());
    }

    private Promocion buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("promociones.no.encontrada"));
    }

    private void aplicar(PromocionRequest request, Promocion promocion) {
        if (!request.hasta().isAfter(request.desde())) {
            throw new BusinessException("promociones.ventana.invalida");
        }
        // Cargar una promoción que ya terminó no rompe nada, pero tampoco hace
        // nada: casi siempre es un error de tipeo en el año o en el mes.
        if (request.hasta().isBefore(LocalDateTime.now())) {
            throw new BusinessException("promociones.ventana.vencida");
        }

        promocion.setNombre(request.nombre().trim());
        promocion.setPorcentaje(request.porcentaje());
        promocion.setAlcance(request.alcance());
        promocion.setDesde(request.desde());
        promocion.setHasta(request.hasta());
        promocion.setProducto(null);
        promocion.setMarca(null);

        switch (request.alcance()) {
            case TODOS -> {
                if (request.productoId() != null || request.marcaId() != null) {
                    throw new BusinessException("promociones.referencia.sobrante");
                }
            }
            case PRODUCTO -> {
                if (request.productoId() == null) {
                    throw new BusinessException("promociones.producto.requerido");
                }
                promocion.setProducto(productoRepository.findById(request.productoId())
                        .orElseThrow(() -> new BusinessException("promociones.producto.no.encontrado")));
            }
            case MARCA -> {
                if (request.marcaId() == null) {
                    throw new BusinessException("promociones.marca.requerida");
                }
                promocion.setMarca(marcaRepository.findById(request.marcaId())
                        .orElseThrow(() -> new BusinessException("promociones.marca.no.encontrada")));
            }
        }
    }
}
