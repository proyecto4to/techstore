package com.techstore.service;

import com.techstore.dto.response.NotaCreditoResponse;
import com.techstore.dto.response.PageResponse;
import com.techstore.entity.EstadoFactura;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.Factura;
import com.techstore.entity.NotaCredito;
import com.techstore.entity.Pedido;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Producto;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.FacturaRepository;
import com.techstore.repository.NotaCreditoRepository;
import com.techstore.repository.PedidoRepository;
import com.techstore.repository.ProductoRepository;
import com.techstore.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * Devolución total de un pedido facturado: emite la nota de crédito,
 * anula la factura, devuelve el stock y registra el egreso en caja —
 * todo en una sola transacción.
 */
@Service
@RequiredArgsConstructor
public class NotaCreditoService {

    private static final String ENTIDAD_NC = "NOTACREDITO";

    private final NotaCreditoRepository notaCreditoRepository;
    private final PedidoRepository pedidoRepository;
    private final FacturaRepository facturaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;
    private final CajaService cajaService;
    private final FacturaPdfService facturaPdfService;

    @Transactional
    public NotaCreditoResponse emitir(Long pedidoId, String motivo, String emailUsuario) {
        Usuario emisor = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        Pedido pedido = pedidoRepository.findWithDetallesById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));

        if (pedido.getEstado() != EstadoPedido.G) {
            throw new BusinessException(
                    "Solo se puede devolver un pedido Pagado (actual: %s)".formatted(pedido.getEstado()));
        }
        Factura factura = facturaRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new BusinessException("El pedido no tiene factura emitida"));
        if (notaCreditoRepository.existsByFacturaId(factura.getId())) {
            throw new BusinessException(
                    "La factura %s ya tiene una nota de crédito".formatted(factura.getNumero()));
        }

        // La mercadería vuelve al inventario.
        productoRepository.findAllForUpdate(pedido.getDetalles().stream()
                .map(detalle -> detalle.getProducto().getId())
                .collect(Collectors.toSet()));
        for (PedidoDetalle detalle : pedido.getDetalles()) {
            Producto producto = detalle.getProducto();
            int stockActual = producto.getStockReal() == null ? 0 : producto.getStockReal();
            producto.setStockReal(stockActual + detalle.getCantidad());
        }

        factura.setEstado(EstadoFactura.Anulada);
        facturaRepository.save(factura);
        pedido.setEstado(EstadoPedido.R);
        pedidoRepository.save(pedido);

        NotaCredito nc = new NotaCredito();
        nc.setNumero("001-001-%07d".formatted(autonumeradorService.siguiente(ENTIDAD_NC)));
        nc.setFactura(factura);
        nc.setPedido(pedido);
        nc.setMotivo(motivo.trim());
        nc.setSubtotal(factura.getSubtotal());
        nc.setIva(factura.getIva());
        nc.setTotal(factura.getTotal());
        nc.setUsuario(emisor);
        nc = notaCreditoRepository.save(nc);

        cajaService.registrarEgresoAutomatico(
                factura.getTotal(),
                "Devolución pedido N.º " + pedido.getNumero(),
                nc.getNumero(),
                emisor);

        return NotaCreditoResponse.from(nc);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotaCreditoResponse> listar(Pageable pageable) {
        return PageResponse.from(notaCreditoRepository.findAll(pageable).map(NotaCreditoResponse::from));
    }

    /** Número de NC + contenido PDF listos para descargar. */
    public record NotaCreditoPdf(String numero, byte[] contenido) {
    }

    @Transactional(readOnly = true)
    public NotaCreditoPdf generarPdf(Long id) {
        NotaCredito nc = notaCreditoRepository.findParaPdfById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nota de crédito", id));
        return new NotaCreditoPdf(nc.getNumero(), facturaPdfService.generarNotaCredito(nc));
    }
}
