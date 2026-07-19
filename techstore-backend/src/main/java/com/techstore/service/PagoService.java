package com.techstore.service;

import com.techstore.dto.request.RegistroPagoRequest;
import com.techstore.dto.response.FacturaResponse;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.PagoResponse;
import com.techstore.entity.EstadoFactura;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.Factura;
import com.techstore.entity.Pago;
import com.techstore.entity.Pedido;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.FacturaRepository;
import com.techstore.repository.PagoRepository;
import com.techstore.repository.PedidoRepository;
import com.techstore.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PagoService {

    private static final String ENTIDAD_PAGO = "PAGO";
    private static final String ENTIDAD_FACTURA = "FACTURA";
    private static final String TIPO_FACTURA_CONTADO = "CONTADO";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final PagoRepository pagoRepository;
    private final FacturaRepository facturaRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;
    private final FacturaPdfService facturaPdfService;
    private final CajaService cajaService;

    /**
     * Registra el cobro total de un pedido confirmado y emite su factura
     * en la misma transacción: si algo falla, no queda ni pago ni factura.
     * Los precios ya incluyen IVA, por lo que el impuesto se desglosa
     * por detalle según su tasa (iva = subtotal * tasa / (100 + tasa)).
     */
    @Transactional
    public PagoResponse registrarPago(Long pedidoId, RegistroPagoRequest request, String emailCajero) {
        Pedido pedido = pedidoRepository.findWithDetallesById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));

        if (pedido.getEstado() != EstadoPedido.C) {
            throw new BusinessException("Solo se puede cobrar un pedido Confirmado (actual: %s)"
                    .formatted(pedido.getEstado()));
        }
        if (pagoRepository.existsByPedidoId(pedidoId)) {
            throw new BusinessException("El pedido N.º %d ya tiene un pago registrado".formatted(pedido.getNumero()));
        }

        Usuario cajero = usuarioRepository.findByEmail(emailCajero)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        Pago pago = new Pago();
        pago.setNumero(autonumeradorService.siguiente(ENTIDAD_PAGO));
        pago.setPedido(pedido);
        pago.setMonto(pedido.getTotal());
        pago.setTipoPago(request.tipoPago());
        pago.setNumeroComprobante(request.numeroComprobante());
        pago.setUsuario(cajero);
        pago = pagoRepository.save(pago);

        BigDecimal iva = calcularIvaIncluido(pedido);

        Factura factura = new Factura();
        factura.setNumero("001-001-%07d".formatted(autonumeradorService.siguiente(ENTIDAD_FACTURA)));
        factura.setTipo(TIPO_FACTURA_CONTADO);
        factura.setPedido(pedido);
        factura.setSubtotal(pedido.getTotal().subtract(iva));
        factura.setIva(iva);
        factura.setTotal(pedido.getTotal());
        factura.setEstado(EstadoFactura.Pagada);
        factura.setUsuario(cajero);
        factura = facturaRepository.save(factura);

        pedido.setEstado(EstadoPedido.G);
        pedidoRepository.save(pedido);

        cajaService.registrarIngresoAutomatico(
                pedido.getTotal(),
                "Cobro pedido N.º " + pedido.getNumero(),
                factura.getNumero(),
                cajero);

        return PagoResponse.from(pago, factura);
    }

    @Transactional(readOnly = true)
    public PageResponse<FacturaResponse> listarFacturas(Pageable pageable) {
        return PageResponse.from(facturaRepository.findAll(pageable).map(FacturaResponse::from));
    }

    @Transactional(readOnly = true)
    public FacturaResponse obtenerFactura(Long id) {
        return facturaRepository.findWithPedidoById(id)
                .map(FacturaResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Factura", id));
    }

    /** Número de factura + contenido PDF listos para descargar. */
    public record FacturaPdf(String numero, byte[] contenido) {
    }

    @Transactional(readOnly = true)
    public FacturaPdf generarPdf(Long id) {
        Factura factura = facturaRepository.findParaPdfById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factura", id));
        return new FacturaPdf(factura.getNumero(), facturaPdfService.generar(factura));
    }

    /**
     * Factura de un pedido para su dueño (o para Admin/Cajero).
     */
    @Transactional(readOnly = true)
    public FacturaPdf generarPdfPorPedido(Long pedidoId, String emailSolicitante, boolean esAdminOCajero) {
        Factura factura = facturaRepository.findParaPdfByPedidoId(pedidoId)
                .orElseThrow(() -> new BusinessException("El pedido aún no tiene factura emitida"));
        if (!esAdminOCajero && !factura.getPedido().getUsuario().getEmail().equals(emailSolicitante)) {
            throw new AccessDeniedException("No puede descargar facturas de otro usuario");
        }
        return new FacturaPdf(factura.getNumero(), facturaPdfService.generar(factura));
    }

    private BigDecimal calcularIvaIncluido(Pedido pedido) {
        BigDecimal iva = BigDecimal.ZERO;
        for (PedidoDetalle detalle : pedido.getDetalles()) {
            BigDecimal tasa = detalle.getIvaAplicado() == null ? BigDecimal.ZERO : detalle.getIvaAplicado();
            if (tasa.signum() > 0) {
                iva = iva.add(detalle.getSubtotal()
                        .multiply(tasa)
                        .divide(CIEN.add(tasa), 2, RoundingMode.HALF_UP));
            }
        }
        return iva;
    }
}
