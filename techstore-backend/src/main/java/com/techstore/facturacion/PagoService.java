package com.techstore.facturacion;

import com.techstore.caja.CajaService;
import com.techstore.comun.AutonumeradorService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.PageResponse;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.comun.RucParaguayo;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import com.techstore.empresa.PuntoExpedicion;
import com.techstore.empresa.PuntoExpedicionService;
import com.techstore.facturacion.sifen.CodigoControl;
import com.techstore.facturacion.sifen.FacturaElectronicaService;
import com.techstore.notificaciones.CorreoService;
import com.techstore.notificaciones.EmailTemplateService;
import com.techstore.notificaciones.TipoCorreo;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoDetalle;
import com.techstore.ventas.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PuntoExpedicionService puntoExpedicionService;

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
                .orElseThrow(() -> new ResourceNotFoundException("ventas.pedido.no.encontrado", pedidoId));

        if (pedido.getEstado() != EstadoPedido.C) {
            throw new BusinessException("facturacion.solo.cobrar.confirmado", pedido.getEstado());
        }
        if (pagoRepository.existsByPedidoId(pedidoId)) {
            throw new BusinessException("facturacion.pedido.ya.cobrado", pedido.getNumero());
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
        // Se exige el emisor completo antes de tomar el número: una factura con
        // el timbrado o la dirección en blanco no sirve, y para cuando se viera
        // el hueco el correlativo ya estaría consumido.
        EmpresaProperties emisor = empresaConfig.actualParaFacturar();
        // Desde qué caja o sucursal se emite lo decide quién está cobrando.
        // Cada punto lleva su propia serie: 001-001-0000001 y 001-002-0000001
        // son dos documentos distintos y los dos válidos.
        PuntoExpedicion punto = puntoExpedicionService.delUsuario(cajero);
        factura.setNumero("%s-%07d".formatted(
                punto.prefijo(),
                autonumeradorService.siguiente(punto.autonumerador(ENTIDAD_FACTURA))));
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
        // La dirección y el teléfono también son independientes del RUC, y
        // opcionales: quedan nulos si no se informaron.
        factura.setReceptorDireccion(receptor == null ? null : limpio(receptor.direccion()));
        factura.setReceptorTelefono(receptor == null ? null : limpio(receptor.telefono()));

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
            throw new BusinessException("facturacion.ruc.invalido", ruc, motivo);
        }

        factura.setReceptorRuc(ruc);
        factura.setReceptorNombre(nombreReceptor(razonSocial, cliente));

        if (receptor != null && Boolean.TRUE.equals(receptor.guardarEnCliente())) {
            cliente.setRuc(ruc);
            cliente.setRazonSocial(factura.getReceptorNombre());
            usuarioRepository.save(cliente);
        }
    }

    /** Texto recortado, o nulo si venía vacío: un dato en blanco no es un dato. */
    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
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
                throw new BusinessException("pago.medio.no.permitido", linea.tipoPago());
            }
            if (!metodosVistos.add(linea.tipoPago())) {
                throw new BusinessException("pago.medio.repetido", linea.tipoPago());
            }
            suma = suma.mas(Dinero.de(linea.monto(), total.moneda()));
        }
        if (suma.compareTo(total) != 0) {
            throw new BusinessException("facturacion.pagos.no.suman.total", suma.plano(), total.plano());
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
                .orElseThrow(() -> new ResourceNotFoundException("facturacion.factura.no.encontrada", id));
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
                .orElseThrow(() -> new BusinessException("facturacion.pedido.sin.factura"));
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
