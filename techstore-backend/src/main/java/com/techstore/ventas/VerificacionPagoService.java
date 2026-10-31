package com.techstore.ventas;

import com.techstore.comun.BusinessException;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.facturacion.PagoService;
import com.techstore.notificaciones.outbox.NotificationOutboxPublisher;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * La tienda revisa las transferencias y decide.
 *
 * <p>Aprobar es cobrar: confirma el pedido —descuenta el stock—, registra el
 * pago por transferencia, emite la factura y pone el envío en preparación, todo
 * en una sola transacción. Si algo falla en el medio (por ejemplo, faltan datos
 * del emisor para facturar) no queda nada a medias: ni el pago aprobado sin
 * factura ni el stock descontado sin cobro.
 *
 * <p>Rechazar no cancela el pedido. El stock sigue reservado y el cliente puede
 * mandar otro comprobante; si no lo hace, el pedido se cancela como cualquier
 * otro pendiente.
 */
@Service
@RequiredArgsConstructor
public class VerificacionPagoService {

    private final PedidoRepository pedidoRepository;
    private final PagoComprobanteRepository comprobanteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PedidoService pedidoService;
    private final PagoService pagoService;
    private final PedidoHistorial historial;
    private final NotificationOutboxPublisher notificaciones;

    /**
     * Los pedidos que esperan algo del pago.
     *
     * @param todos falso trae solo los que tienen un comprobante para revisar;
     *              verdadero suma los que esperan un comprobante del cliente
     *              (nunca lo mandó, o se le rechazó el último)
     */
    @Transactional(readOnly = true)
    public List<PagoPendienteResponse> pendientes(boolean todos) {
        EnumSet<EstadoPago> estados = todos
                ? EnumSet.of(EstadoPago.EN_VERIFICACION, EstadoPago.PENDIENTE_COMPROBANTE, EstadoPago.RECHAZADO)
                : EnumSet.of(EstadoPago.EN_VERIFICACION);
        return pedidoRepository.findByTipoAndEstadoPagoInOrderByFechaAsc(TipoPedido.Venta, estados).stream()
                .filter(pedido -> pedido.getEstado() != EstadoPedido.X)
                .map(pedido -> new PagoPendienteResponse(
                        pedido.getId(),
                        pedido.getNumero(),
                        pedido.getFecha(),
                        pedido.getTotal().monto(),
                        pedido.getMoneda().name(),
                        pedido.getMetodoEntrega() == null ? null : pedido.getMetodoEntrega().name(),
                        pedido.getEstadoPago().name(),
                        (pedido.getUsuario().getNombre() + " " + pedido.getUsuario().getApellido()).trim(),
                        pedido.getUsuario().getEmail(),
                        comprobanteRepository.countByPedidoIdAndEstado(pedido.getId(), EstadoComprobante.EN_REVISION),
                        pedido.getPagoRechazoMotivo()))
                .toList();
    }

    /** Cuántos esperan revisión, para el número de la baldosa. */
    @Transactional(readOnly = true)
    public long cuantosParaRevisar() {
        return pedidoRepository.countByTipoAndEstadoPago(TipoPedido.Venta, EstadoPago.EN_VERIFICACION);
    }

    @Transactional
    public PedidoResponse aprobar(Long pedidoId, AprobacionPagoRequest request, String emailVerificador) {
        // El candado serializa dos aprobaciones del mismo pedido: dos personas
        // mirando la misma lista pueden tocar «Aprobar» casi a la vez.
        Pedido pedido = pedidoRepository.findWithDetallesForUpdateById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("ventas.pedido.no.encontrado", pedidoId));
        exigirEnVerificacion(pedido);
        if (pedido.getEstado() != EstadoPedido.P && pedido.getEstado() != EstadoPedido.C) {
            throw new BusinessException("pagos.verificacion.estado.pedido", pedido.getNumero(), pedido.getEstado());
        }
        Usuario verificador = usuario(emailVerificador);
        LocalDateTime ahora = LocalDateTime.now();

        // Primero el pago aprobado: es lo que habilita confirmar un pedido con
        // pago previo. Si lo que sigue falla, la transacción entera vuelve atrás.
        pedido.setEstadoPago(EstadoPago.APROBADO);
        pedido.setPagoRechazoMotivo(null);
        pedido.setPagoVerificadoPor(verificador);
        pedido.setPagoVerificadoEn(ahora);
        for (PagoComprobante comprobante :
                comprobanteRepository.findByPedidoIdAndEstado(pedidoId, EstadoComprobante.EN_REVISION)) {
            comprobante.setEstado(EstadoComprobante.APROBADO);
            comprobante.setRevisadoPor(verificador);
            comprobante.setRevisadoEn(ahora);
        }
        pedidoRepository.save(pedido);
        historial.registrar(pedido, TipoEventoPedido.PAGO_APROBADO,
                "Verificamos la transferencia", verificador);

        if (pedido.getEstado() == EstadoPedido.P) {
            pedidoService.confirmar(pedidoId, emailVerificador);
        }
        String referencia = request == null || request.referencia() == null || request.referencia().isBlank()
                ? null : request.referencia().trim();
        pagoService.registrarPagoTransferenciaVerificada(pedidoId, referencia, emailVerificador);
        return pedidoService.obtener(pedidoId, emailVerificador, true);
    }

    @Transactional
    public PedidoResponse rechazar(Long pedidoId, RechazoPagoRequest request, String emailVerificador) {
        Pedido pedido = pedidoRepository.findWithDetallesForUpdateById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("ventas.pedido.no.encontrado", pedidoId));
        exigirEnVerificacion(pedido);
        Usuario verificador = usuario(emailVerificador);
        LocalDateTime ahora = LocalDateTime.now();
        String motivo = request.motivo().trim();

        List<PagoComprobante> enRevision =
                comprobanteRepository.findByPedidoIdAndEstado(pedidoId, EstadoComprobante.EN_REVISION);
        Long ultimo = null;
        for (PagoComprobante comprobante : enRevision) {
            comprobante.setEstado(EstadoComprobante.RECHAZADO);
            comprobante.setMotivoRechazo(motivo);
            comprobante.setRevisadoPor(verificador);
            comprobante.setRevisadoEn(ahora);
            ultimo = comprobante.getId();
        }
        pedido.setEstadoPago(EstadoPago.RECHAZADO);
        pedido.setPagoRechazoMotivo(motivo);
        pedido.setPagoVerificadoPor(verificador);
        pedido.setPagoVerificadoEn(ahora);
        pedidoRepository.save(pedido);
        historial.registrar(pedido, TipoEventoPedido.PAGO_RECHAZADO, motivo, verificador);
        notificaciones.pagoRechazado(pedido, ultimo == null ? pedidoId : ultimo);
        return pedidoService.obtener(pedidoId, emailVerificador, true);
    }

    private void exigirEnVerificacion(Pedido pedido) {
        if (pedido.getEstadoPago() != EstadoPago.EN_VERIFICACION) {
            throw new BusinessException("pagos.verificacion.no.pendiente", pedido.getNumero());
        }
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
    }
}
