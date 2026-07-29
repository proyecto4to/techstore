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
import com.techstore.entity.TipoCorreo;
import com.techstore.entity.TipoPago;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.FacturaRepository;
import com.techstore.repository.PagoRepository;
import com.techstore.repository.PedidoRepository;
import com.techstore.repository.UsuarioRepository;
import com.techstore.sifen.CodigoControl;
import com.techstore.sifen.FacturaElectronicaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PagoService {

    private static final String ENTIDAD_PAGO = "PAGO";
    private static final String ENTIDAD_FACTURA = "FACTURA";
    private static final String TIPO_FACTURA_CONTADO = "CONTADO";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /** Métodos de pago habilitados; Tarjeta y Cheque quedan fuera de uso. */
    private static final Set<TipoPago> METODOS_HABILITADOS =
            EnumSet.of(TipoPago.Efectivo, TipoPago.Transferencia);

    private final PagoRepository pagoRepository;
    private final FacturaRepository facturaRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;
    private final CajaService cajaService;
    private final CorreoService correoService;
    private final EmailTemplateService emailTemplateService;
    private final FacturaElectronicaService facturaElectronicaService;

    /**
     * Registra el cobro de un pedido confirmado y emite su factura en la
     * misma transacción: si algo falla, no queda ni pago ni factura. El
     * cobro puede repartirse entre efectivo y transferencia (pago mixto);
     * la suma de las líneas debe igualar el total del pedido.
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

        validarLineas(request.lineas(), pedido.getTotal());

        Usuario cajero = usuarioRepository.findByEmail(emailCajero)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        List<Pago> pagos = new ArrayList<>();
        for (RegistroPagoRequest.LineaPago linea : request.lineas()) {
            Pago pago = new Pago();
            pago.setNumero(autonumeradorService.siguiente(ENTIDAD_PAGO));
            pago.setPedido(pedido);
            pago.setMonto(linea.monto());
            pago.setTipoPago(linea.tipoPago());
            pago.setNumeroComprobante(linea.numeroComprobante());
            pago.setUsuario(cajero);
            pagos.add(pagoRepository.save(pago));
        }

        BigDecimal iva = calcularIvaIncluido(pedido);

        Factura factura = new Factura();
        factura.setNumero("001-001-%07d".formatted(autonumeradorService.siguiente(ENTIDAD_FACTURA)));
        // Parte del CDC: se fija ahora para que el documento electrónico sea siempre el mismo.
        factura.setCodigoSeguridad(CodigoControl.generarCodigoSeguridad());
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

        // Un movimiento de caja por método, para que el arqueo distinga
        // el efectivo recibido de las transferencias.
        for (Pago pago : pagos) {
            cajaService.registrarIngresoAutomatico(
                    pago.getMonto(),
                    "Cobro pedido N.º " + pedido.getNumero() + " (" + pago.getTipoPago() + ")",
                    factura.getNumero(),
                    cajero,
                    pago.getTipoPago());
        }

        enviarFacturaPorCorreo(pedido, factura);

        return PagoResponse.from(pagos, factura);
    }

    private void validarLineas(List<RegistroPagoRequest.LineaPago> lineas, BigDecimal total) {
        Set<TipoPago> metodosVistos = new HashSet<>();
        BigDecimal suma = BigDecimal.ZERO;
        for (RegistroPagoRequest.LineaPago linea : lineas) {
            if (!METODOS_HABILITADOS.contains(linea.tipoPago())) {
                throw new BusinessException(
                        "Método de pago no permitido: %s. Solo se aceptan Efectivo y Transferencia."
                                .formatted(linea.tipoPago()));
            }
            if (!metodosVistos.add(linea.tipoPago())) {
                throw new BusinessException(
                        "El método %s aparece más de una vez; combiná los montos en una sola línea."
                                .formatted(linea.tipoPago()));
            }
            suma = suma.add(linea.monto());
        }
        if (suma.compareTo(total) != 0) {
            throw new BusinessException(
                    "La suma de los pagos (%s) no coincide con el total del pedido (%s)."
                            .formatted(suma.toPlainString(), total.toPlainString()));
        }
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
        // La factura que se descarga es la representación gráfica e-kuatia (KuDE):
        // encabezado del emisor, datos del receptor, columnas por tasa de IVA, QR y CDC.
        FacturaElectronicaService.Kude kude = facturaElectronicaService.generarKude(id);
        return new FacturaPdf(kude.numero(), kude.pdf());
    }

    /**
     * Factura de un pedido para su dueño (o para Admin/Cajero), en formato e-kuatia (KuDE).
     */
    @Transactional(readOnly = true)
    public FacturaPdf generarPdfPorPedido(Long pedidoId, String emailSolicitante, boolean esAdminOCajero) {
        Factura factura = facturaRepository.findParaPdfByPedidoId(pedidoId)
                .orElseThrow(() -> new BusinessException("El pedido aún no tiene factura emitida"));
        if (!esAdminOCajero && !factura.getPedido().getUsuario().getEmail().equals(emailSolicitante)) {
            throw new AccessDeniedException("No puede descargar facturas de otro usuario");
        }
        FacturaElectronicaService.Kude kude = facturaElectronicaService.generarKude(factura.getId());
        return new FacturaPdf(kude.numero(), kude.pdf());
    }

    /**
     * Envía la factura recién emitida al cliente con el PDF adjunto (bandeja de salida).
     * El adjunto es el mismo KuDE que se descarga desde el panel y desde "Mis pedidos",
     * para que el cliente reciba exactamente el documento que ve en el sistema.
     */
    private void enviarFacturaPorCorreo(Pedido pedido, Factura factura) {
        Usuario cliente = pedido.getUsuario();
        // El cobro ya está registrado: si fallara el armado del KuDE, se manda el aviso
        // sin adjunto en vez de tirar abajo toda la transacción del pago.
        byte[] pdf;
        try {
            pdf = facturaElectronicaService.generarKude(factura.getId()).pdf();
        } catch (Exception ex) {
            pdf = null;
            log.warn("No se pudo generar el KuDE de la factura {} para adjuntarlo al correo: {}",
                    factura.getNumero(), ex.getMessage());
        }
        String cuerpo = emailTemplateService.construir(
                TipoCorreo.FACTURA,
                "¡Gracias por tu compra!",
                cliente.getNombre(),
                "Adjuntamos la factura " + factura.getNumero() + " correspondiente a tu compra "
                        + "(pedido N.º " + pedido.getNumero() + ").",
                "Total: Gs. " + factura.getTotal().toPlainString(),
                "VER MIS PEDIDOS",
                emailTemplateService.url("/pedidos"));
        correoService.enviar(
                TipoCorreo.FACTURA,
                cliente.getEmail(),
                cliente.getNombre() + " " + cliente.getApellido(),
                "Tu factura " + factura.getNumero() + " — TechStore",
                cuerpo,
                factura.getNumero(),
                pdf,
                "factura-" + factura.getNumero() + ".pdf");
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
