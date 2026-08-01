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
import com.techstore.config.EmpresaProperties;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.FacturaRepository;
import com.techstore.repository.PagoRepository;
import com.techstore.repository.PedidoRepository;
import com.techstore.repository.UsuarioRepository;
import com.techstore.sifen.CodigoControl;
import com.techstore.sifen.FacturaElectronicaService;
import com.techstore.util.Dinero;
import com.techstore.util.RucParaguayo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final EmpresaConfigService empresaConfig;
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
            pago.setMoneda(pedido.getMoneda());
            pago.setMonto(Dinero.de(linea.monto(), pedido.getMoneda()));
            pago.setTipoPago(linea.tipoPago());
            pago.setNumeroComprobante(linea.numeroComprobante());
            pago.setUsuario(cajero);
            pagos.add(pagoRepository.save(pago));
        }

        Dinero iva = calcularIvaIncluido(pedido);

        Factura factura = new Factura();
        factura.setMoneda(pedido.getMoneda());
        // El prefijo sale de los datos del emisor: tiene que coincidir con el que
        // el CDC arma con el establecimiento y el punto de expedición.
        EmpresaProperties emisor = empresaConfig.actual();
        factura.setNumero("%s-%s-%07d".formatted(
                emisor.establecimiento(), emisor.puntoExpedicion(),
                autonumeradorService.siguiente(ENTIDAD_FACTURA)));
        // Parte del CDC: se fija ahora para que el documento electrónico sea siempre el mismo.
        factura.setCodigoSeguridad(CodigoControl.generarCodigoSeguridad());
        factura.setTipo(TIPO_FACTURA_CONTADO);
        factura.setPedido(pedido);
        factura.setSubtotal(pedido.getTotal().menos(iva));
        factura.setIva(iva);
        factura.setTotal(pedido.getTotal());
        factura.setEstado(EstadoFactura.Pagada);
        factura.setUsuario(cajero);
        aplicarReceptor(factura, pedido.getUsuario(), request.receptor());
        factura = facturaRepository.save(factura);

        pedido.setEstado(EstadoPedido.G);
        pedidoRepository.save(pedido);

        // Un movimiento de caja por método, para que el arqueo distinga
        // el efectivo recibido de las transferencias.
        for (Pago pago : pagos) {
            // "FAC" adelante: las facturas y las notas de crédito comparten el
            // formato 001-001-NNNNNNN con numeradores independientes, así que
            // sin el prefijo dos movimientos sin relación entre sí terminaban
            // con la misma referencia.
            cajaService.registrarIngresoAutomatico(
                    pago.getMonto(),
                    "Cobro pedido N.º " + pedido.getNumero() + " (" + pago.getTipoPago() + ")",
                    "FAC " + factura.getNumero(),
                    cajero,
                    pago.getTipoPago());
        }

        enviarFacturaPorCorreo(pedido, factura);

        return PagoResponse.from(pagos, factura);
    }

    /**
     * Congela en la factura a quién se le emite. Si el cobro trae un RUC se
     * factura a ese contribuyente; si no, se usa el que el cliente tenga
     * cargado en su cuenta; y si no hay ninguno, sale a consumidor final.
     */
    private void aplicarReceptor(Factura factura, Usuario cliente, RegistroPagoRequest.Receptor receptor) {
        String ruc = receptor == null ? null : RucParaguayo.normalizar(receptor.ruc());
        String razonSocial = receptor == null ? null : receptor.razonSocial();

        // El correo es independiente del RUC: un cliente de mostrador puede querer
        // su factura por correo aunque compre como consumidor final.
        String email = receptor == null || receptor.email() == null || receptor.email().isBlank()
                ? null
                : receptor.email().trim();
        factura.setReceptorEmail(email);

        if (ruc == null) {
            // Sin RUC en el cobro: se cae al de la cuenta del cliente, si tiene.
            ruc = RucParaguayo.normalizar(cliente.getRuc());
            razonSocial = cliente.getRazonSocial();
        }
        if (ruc == null) {
            return; // consumidor final: receptorRuc queda nulo
        }
        String motivo = RucParaguayo.motivoInvalidez(ruc);
        if (motivo != null) {
            throw new BusinessException("El RUC '%s' no es válido: %s.".formatted(ruc, motivo));
        }

        factura.setReceptorRuc(ruc);
        factura.setReceptorNombre(nombreReceptor(razonSocial, cliente));

        if (receptor != null && Boolean.TRUE.equals(receptor.guardarEnCliente())) {
            cliente.setRuc(ruc);
            cliente.setRazonSocial(factura.getReceptorNombre());
            usuarioRepository.save(cliente);
        }
    }

    private static String nombreReceptor(String razonSocial, Usuario cliente) {
        if (razonSocial != null && !razonSocial.isBlank()) {
            return razonSocial.trim();
        }
        return cliente.getNombre() + " " + cliente.getApellido();
    }

    private void validarLineas(List<RegistroPagoRequest.LineaPago> lineas, Dinero total) {
        Set<TipoPago> metodosVistos = new HashSet<>();
        Dinero suma = Dinero.cero(total.moneda());
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
            suma = suma.mas(Dinero.de(linea.monto(), total.moneda()));
        }
        if (suma.compareTo(total) != 0) {
            throw new BusinessException(
                    "La suma de los pagos (%s) no coincide con el total del pedido (%s)."
                            .formatted(suma.plano(), total.plano()));
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
        // Si se informó el correo del receptor, la factura va ahí: en una venta de
        // mostrador el pedido es del cajero, y el comprobante es del cliente.
        boolean aReceptor = factura.getReceptorEmail() != null;
        String destinatario = aReceptor ? factura.getReceptorEmail() : cliente.getEmail();
        String nombre = aReceptor
                ? (factura.getReceptorNombre() != null ? factura.getReceptorNombre() : "cliente")
                : cliente.getNombre() + " " + cliente.getApellido();

        String cuerpo = emailTemplateService.construir(
                TipoCorreo.FACTURA,
                "¡Gracias por tu compra!",
                nombre,
                "Adjuntamos la factura " + factura.getNumero() + " correspondiente a tu compra "
                        + "(pedido N.º " + pedido.getNumero() + ").",
                "Total: " + factura.getTotal(),
                aReceptor ? null : "VER MIS PEDIDOS",
                aReceptor ? null : emailTemplateService.url("/pedidos"));
        correoService.enviar(
                TipoCorreo.FACTURA,
                destinatario,
                nombre,
                "Tu factura " + factura.getNumero() + " — TechStore",
                cuerpo,
                factura.getNumero(),
                pdf,
                "factura-" + factura.getNumero() + ".pdf");
    }

    /**
     * El IVA contenido en el pedido, sumado detalle por detalle porque cada uno
     * puede tener su propia tasa. El cálculo y el redondeo los hace {@link Dinero}
     * según la moneda: es el mismo que usan la nota de crédito y el KuDE.
     */
    private Dinero calcularIvaIncluido(Pedido pedido) {
        Dinero iva = Dinero.cero(pedido.getMoneda());
        for (PedidoDetalle detalle : pedido.getDetalles()) {
            iva = iva.mas(detalle.getSubtotal().ivaIncluido(detalle.getIvaAplicado()));
        }
        return iva;
    }
}
