package com.techstore.empresa;

import com.techstore.comun.BusinessException;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.usuarios.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Desde qué punto de expedición se está emitiendo.
 *
 * <p>Se detecta por el usuario que factura: cada cajero pertenece a una caja o
 * sucursal. Si no tiene ninguno asignado se usa el punto por defecto de la
 * empresa, que es el que venía funcionando antes de que hubiera varios.
 */
@Service
@RequiredArgsConstructor
public class PuntoExpedicionService {

    private final PuntoExpedicionRepository repo;

    @Transactional(readOnly = true)
    public List<PuntoExpedicionResponse> listar() {
        return repo.findAll().stream().map(PuntoExpedicionResponse::from).toList();
    }

    /**
     * El punto desde el que emite este usuario. Nunca devuelve nulo: si el
     * usuario no tiene uno, cae al por defecto.
     */
    @Transactional(readOnly = true)
    public PuntoExpedicion delUsuario(Usuario usuario) {
        PuntoExpedicion propio = usuario.getPuntoExpedicion();
        if (propio != null && Boolean.TRUE.equals(propio.getActivo())) {
            return propio;
        }
        // Un punto desactivado no puede emitir: se cae al por defecto en vez de
        // frenar la venta, y queda registrado en la factura desde dónde salió.
        return porDefecto();
    }

    @Transactional(readOnly = true)
    public PuntoExpedicion porDefecto() {
        return repo.findByPorDefectoTrue()
                .orElseThrow(() -> new BusinessException("empresa.sin.punto.expedicion"));
    }

    @Transactional
    public PuntoExpedicionResponse crear(PuntoExpedicionRequest request) {
        repo.findByEstablecimientoAndPunto(request.establecimiento(), request.punto())
                .ifPresent(p -> {
                    throw new BusinessException("empresa.punto.expedicion.duplicado",
                            request.establecimiento(), request.punto());
                });

        PuntoExpedicion punto = new PuntoExpedicion();
        punto.setEstablecimiento(request.establecimiento());
        punto.setPunto(request.punto());
        punto.setNombre(request.nombre());
        punto.setActivo(request.activo() == null || request.activo());
        // El por defecto no se toca desde acá: cambiarlo movería la serie que
        // usan las facturas históricas.
        punto.setPorDefecto(false);
        return PuntoExpedicionResponse.from(repo.save(punto));
    }

    @Transactional
    public PuntoExpedicionResponse cambiarActivo(Long id, boolean activo) {
        PuntoExpedicion punto = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("empresa.punto.expedicion.no.encontrado", id));
        if (!activo && Boolean.TRUE.equals(punto.getPorDefecto())) {
            throw new BusinessException("empresa.punto.expedicion.defecto.no.desactivable");
        }
        punto.setActivo(activo);
        return PuntoExpedicionResponse.from(punto);
    }
}
