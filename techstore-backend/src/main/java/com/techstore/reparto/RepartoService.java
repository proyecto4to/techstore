package com.techstore.reparto;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.caja.CajaService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.PageResponse;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.envios.Envio;
import com.techstore.envios.EnvioRepository;
import com.techstore.envios.EnvioService;
import com.techstore.envios.TipoEnvio;
import com.techstore.facturacion.PagoService;
import com.techstore.facturacion.TipoPago;
import com.techstore.notificaciones.outbox.NotificationEventType;
import com.techstore.notificaciones.outbox.NotificationOutboxPublisher;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import com.techstore.usuarios.UsuarioResponse;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.MetodoEntrega;
import com.techstore.ventas.MetodoPagoCheckout;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoRepository;
import com.techstore.ventas.TipoPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RepartoService {
    private static final Set<EstadoReparto> ACTIVOS =
            EnumSet.of(EstadoReparto.ASIGNADO, EstadoReparto.ACEPTADO,
                    EstadoReparto.EN_RUTA, EstadoReparto.EN_DESTINO);

    private final AsignacionRepartoRepository asignacionRepository;
    private final RepartoHistorialRepository historialRepository;
    private final CobroRepartidorRepository cobroRepository;
    private final MovimientoCajaRepartidorRepository movimientoRepository;
    private final CierreCajaRepartidorRepository cierreRepository;
    private final TransferenciaRepartidorCajeroRepository transferenciaRepository;
    private final PedidoRepository pedidoRepository;
    private final EnvioRepository envioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoService pagoService;
    private final CajaService cajaService;
    private final EnvioService envioService;
    private final OptimizadorRuta optimizadorRuta;
    private final NotificationOutboxPublisher notificationPublisher;
    private final AuditoriaService auditoriaService;

    @Transactional
    public RepartoResponse asignar(Long pedidoId, AsignarRepartoRequest request, String emailAdmin) {
        Pedido pedido = pedidoRepository.findWithDetallesForUpdateById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("ventas.pedido.no.encontrado", pedidoId));
        if (pedido.getTipo() != TipoPedido.Venta
                || pedido.getMetodoEntrega() != MetodoEntrega.ENVIO_DOMICILIO
                || !Set.of(EstadoPedido.C, EstadoPedido.G).contains(pedido.getEstado())) {
            throw new BusinessException("reparto.pedido.no.asignable");
        }
        Envio envio = envioRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new BusinessException("envios.pedido.sin.envio", pedidoId));
        if (envio.getTipo() == TipoEnvio.RETIRO_TIENDA) {
            throw new BusinessException("reparto.pedido.no.asignable");
        }
        Usuario repartidor = usuarioRepository.findWithRolById(request.repartidorId())
                .orElseThrow(() -> new ResourceNotFoundException("usuarios.no.encontrado", request.repartidorId()));
        exigirRol(repartidor, "Repartidor");
        Usuario admin = usuario(emailAdmin);

        AsignacionReparto anterior = asignacionRepository
                .findActiveForUpdateByPedidoId(pedidoId, ACTIVOS).orElse(null);
        if (anterior != null && anterior.getRepartidor().getId().equals(repartidor.getId())) {
            anterior.setOrdenEntrega(request.ordenEntrega());
            anterior.setObservaciones(limpio(request.observaciones()));
            return respuesta(asignacionRepository.save(anterior));
        }
        if (anterior != null) {
            EstadoReparto previo = anterior.getEstado();
            anterior.setEstado(EstadoReparto.CANCELADO);
            anterior.setFechaFinalizacion(LocalDateTime.now());
            anterior.setResultadoFinal("Reasignado por " + emailAdmin);
            // La restricción parcial permite una sola asignación activa por pedido.
            // Forzamos el UPDATE antes del INSERT para que PostgreSQL vea la anterior cancelada.
            asignacionRepository.saveAndFlush(anterior);
            historial(anterior, previo, EstadoReparto.CANCELADO, admin, "Pedido reasignado");
            notificar(NotificationEventType.REPARTO_REASIGNADO, anterior, anterior.getRepartidor(),
                    "Entrega reasignada", "El pedido #" + pedido.getNumero() + " fue reasignado.");
        }

        AsignacionReparto asignacion = new AsignacionReparto();
        asignacion.setPedido(pedido);
        asignacion.setEnvio(envio);
        asignacion.setRepartidor(repartidor);
        asignacion.setAsignadoPor(admin);
        asignacion.setEstado(EstadoReparto.ASIGNADO);
        asignacion.setOrdenEntrega(request.ordenEntrega());
        asignacion.setObservaciones(limpio(request.observaciones()));
        asignacion = asignacionRepository.save(asignacion);
        historial(asignacion, null, EstadoReparto.ASIGNADO, admin,
                anterior == null ? "Asignación inicial" : "Nueva asignación");
        envioService.reflejarReparto(pedido, EstadoReparto.ASIGNADO, request.observaciones());
        notificar(anterior == null ? NotificationEventType.REPARTO_ASIGNADO
                        : NotificationEventType.REPARTO_REASIGNADO,
                asignacion, repartidor, "Nueva entrega asignada",
                "Tenés asignado el pedido #" + pedido.getNumero() + ".");
        auditoriaService.registrar("AsignacionReparto", asignacion.getId(), AuditoriaService.Accion.Crear,
                "Asignó pedido #" + pedido.getNumero() + " a " + repartidor.getEmail(),
                AuditoriaService.Cambios.nuevo().valor("repartidor", repartidor.getEmail())
                        .valor("ordenEntrega", request.ordenEntrega()));
        return respuesta(asignacion);
    }

    @Transactional(readOnly = true)
    public List<RepartoResponse> asignacionesPedido(Long pedidoId) {
        if (!pedidoRepository.existsById(pedidoId)) {
            throw new ResourceNotFoundException("ventas.pedido.no.encontrado", pedidoId);
        }
        return asignacionRepository.findByPedidoIdOrderByFechaAsignacionDescIdDesc(pedidoId).stream()
                .map(this::respuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RepartoResponse> listarPropias(
            String email, Collection<EstadoReparto> estados, Pageable pageable) {
        var page = estados == null || estados.isEmpty()
                ? asignacionRepository.findByRepartidorEmail(email, pageable)
                : asignacionRepository.findByRepartidorEmailAndEstadoIn(email, estados, pageable);
        return PageResponse.from(page.map(this::respuesta));
    }

    @Transactional(readOnly = true)
    public List<EntregasClienteResponse> agruparPorCliente(String email) {
        List<AsignacionReparto> asignaciones = asignacionRepository
                .findByRepartidorEmailAndEstadoInOrderByOrdenEntregaAscFechaAsignacionAsc(email, ACTIVOS);
        Map<String, List<RepartoResponse>> grupos = new LinkedHashMap<>();
        for (AsignacionReparto asignacion : asignaciones) {
            RepartoResponse r = respuesta(asignacion);
            String key = r.clienteId() + "|" + r.direccion();
            grupos.computeIfAbsent(key, ignored -> new ArrayList<>()).add(r);
        }
        return grupos.values().stream().map(lista -> {
            RepartoResponse primero = lista.get(0);
            return new EntregasClienteResponse(primero.clienteId(), primero.cliente(),
                    primero.direccion(), lista);
        }).toList();
    }

    @Transactional(readOnly = true)
    public RutaOptimizadaResponse ruta(String email, BigDecimal latitud, BigDecimal longitud) {
        List<AsignacionReparto> asignaciones = asignacionRepository
                .findByRepartidorEmailAndEstadoInOrderByOrdenEntregaAscFechaAsignacionAsc(email, ACTIVOS);
        return optimizadorRuta.optimizar(asignaciones, latitud, longitud);
    }

    @Transactional(readOnly = true)
    public List<HistorialRepartoResponse> historial(Long id, String email, boolean admin) {
        AsignacionReparto asignacion = asignacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("reparto.asignacion.no.encontrada", id));
        if (!admin && !asignacion.getRepartidor().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("No puede consultar entregas de otro repartidor");
        }
        return historialRepository.findByAsignacionIdOrderByFechaAscIdAsc(id).stream()
                .map(HistorialRepartoResponse::from).toList();
    }

    @Transactional
    public RepartoResponse cambiarEstado(Long id, CambiarEstadoRepartoRequest request, String email) {
        AsignacionReparto asignacion = propiaParaActualizar(id, email);
        EstadoReparto anterior = asignacion.getEstado();
        if (!transiciones(anterior).contains(request.estado())) {
            throw new BusinessException("reparto.estado.transicion.invalida", anterior, request.estado());
        }
        if (request.estado() == EstadoReparto.NO_ENTREGADO
                && limpio(request.resultadoFinal()) == null) {
            throw new BusinessException("reparto.resultado.requerido");
        }
        if (request.estado() == EstadoReparto.ENTREGADO) {
            validarPagoParaEntrega(asignacion);
        }

        LocalDateTime ahora = LocalDateTime.now();
        asignacion.setEstado(request.estado());
        if (request.estado() == EstadoReparto.ACEPTADO) asignacion.setFechaAceptacion(ahora);
        if (request.estado() == EstadoReparto.EN_RUTA && asignacion.getFechaInicio() == null) {
            asignacion.setFechaInicio(ahora);
        }
        if (request.estado().terminal()) {
            asignacion.setFechaFinalizacion(ahora);
            asignacion.setResultadoFinal(limpio(request.resultadoFinal()));
        }
        if (limpio(request.observacion()) != null) asignacion.setObservaciones(limpio(request.observacion()));
        asignacion = asignacionRepository.save(asignacion);
        historial(asignacion, anterior, request.estado(), asignacion.getRepartidor(), request.observacion());
        envioService.reflejarReparto(asignacion.getPedido(), request.estado(), request.observacion());
        if (request.estado() == EstadoReparto.ENTREGADO) {
            asignacion.getPedido().setEstado(EstadoPedido.N);
            pedidoRepository.save(asignacion.getPedido());
        }
        notificar(NotificationEventType.REPARTO_ACTUALIZADO, asignacion, asignacion.getRepartidor(),
                "Entrega actualizada", "Pedido #" + asignacion.getPedido().getNumero()
                        + ": " + request.estado().name() + ".");
        auditoriaService.registrar("AsignacionReparto", asignacion.getId(), AuditoriaService.Accion.Editar,
                "Cambió estado de " + anterior + " a " + request.estado(),
                AuditoriaService.Cambios.nuevo().de("estado", anterior, request.estado()));
        return respuesta(asignacion);
    }

    @Transactional
    public RepartoResponse registrarCobro(Long id, String email) {
        AsignacionReparto asignacion = propiaParaActualizar(id, email);
        if (!Set.of(EstadoReparto.EN_RUTA, EstadoReparto.EN_DESTINO).contains(asignacion.getEstado())) {
            throw new BusinessException("reparto.cobro.estado.invalido");
        }
        Pedido pedido = asignacion.getPedido();
        if (pedido.getMetodoPago() != MetodoPagoCheckout.PAGO_EFECTIVO_REPARTIDOR) {
            throw new BusinessException("reparto.cobro.medio.invalido");
        }
        if (cobroRepository.existsByPedidoId(pedido.getId())
                || cobroRepository.existsByAsignacionId(asignacion.getId())) {
            throw new BusinessException("reparto.cobro.duplicado");
        }

        pagoService.registrarPagoRepartidor(pedido.getId(), email);
        CobroRepartidor cobro = new CobroRepartidor();
        cobro.setPedido(pedido);
        cobro.setAsignacion(asignacion);
        cobro.setCliente(pedido.getUsuario());
        cobro.setRepartidor(asignacion.getRepartidor());
        cobro.setMoneda(pedido.getMoneda());
        cobro.setMonto(pedido.getTotal());
        cobro.setRegistradoPor(asignacion.getRepartidor());
        cobro.setReferencia("PEDIDO-" + pedido.getId());
        cobro = cobroRepository.save(cobro);
        movimiento(asignacion.getRepartidor(), TipoMovimientoCajaRepartidor.COBRO_PEDIDO,
                NaturalezaMovimiento.CREDITO, pedido.getTotal(), "COBRO_REPARTIDOR", cobro.getId(),
                asignacion.getRepartidor(), "Cobro del pedido #" + pedido.getNumero());
        auditoriaService.registrar("CobroRepartidor", cobro.getId(), AuditoriaService.Accion.Cobrar,
                "Cobró en efectivo el pedido #" + pedido.getNumero(),
                AuditoriaService.Cambios.nuevo().valor("monto", pedido.getTotal()));
        return respuesta(asignacion);
    }

    @Transactional(readOnly = true)
    public DashboardRepartidorResponse dashboard(String email) {
        Usuario repartidor = usuario(email);
        BigDecimal saldo = saldo(repartidor.getId());
        return new DashboardRepartidorResponse(
                asignacionRepository.countByRepartidorEmailAndEstadoIn(email,
                        List.of(EstadoReparto.ASIGNADO, EstadoReparto.ACEPTADO)),
                asignacionRepository.countByRepartidorEmailAndEstadoIn(email,
                        List.of(EstadoReparto.EN_RUTA, EstadoReparto.EN_DESTINO)),
                asignacionRepository.countByRepartidorEmailAndEstado(email, EstadoReparto.ENTREGADO),
                cobroRepository.countByRepartidorEmailAndEstado(email, EstadoMovimientoCaja.REGISTRADO),
                saldo, Moneda.porDefecto().codigo(),
                transferenciaRepository.countByRepartidorEmailAndEstado(
                        email, EstadoTransferencia.PENDIENTE_ACEPTACION));
    }

    @Transactional(readOnly = true)
    public BigDecimal saldoActual(String email) {
        return saldo(usuario(email).getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<MovimientoCajaRepartidorResponse> movimientos(String email, Pageable pageable) {
        return PageResponse.from(movimientoRepository
                .findByRepartidorEmailOrderByFechaDescIdDesc(email, pageable)
                .map(MovimientoCajaRepartidorResponse::from));
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> cajeros() {
        return usuarioRepository.findByRolNombreInAndActivoTrue(List.of("Cajero")).stream()
                .map(UsuarioResponse::from).toList();
    }

    @Transactional
    public TransferenciaResponse solicitarVaciado(String email, SolicitarTransferenciaRequest request) {
        Usuario repartidorSinLock = usuario(email);
        Usuario repartidor = usuarioRepository.findByIdForUpdate(repartidorSinLock.getId()).orElseThrow();
        exigirRol(repartidor, "Repartidor");
        if (cierreRepository.existsByRepartidorIdAndEstado(
                repartidor.getId(), EstadoTransferencia.PENDIENTE_ACEPTACION)) {
            throw new BusinessException("reparto.transferencia.pendiente.existente");
        }
        BigDecimal saldo = saldo(repartidor.getId());
        if (saldo.signum() <= 0) throw new BusinessException("reparto.caja.sin.saldo");
        Usuario cajero = usuarioRepository.findWithRolById(request.cajeroId())
                .orElseThrow(() -> new ResourceNotFoundException("usuarios.no.encontrado", request.cajeroId()));
        exigirRol(cajero, "Cajero");

        Dinero monto = Dinero.de(saldo, Moneda.porDefecto());
        CierreCajaRepartidor cierre = new CierreCajaRepartidor();
        cierre.setRepartidor(repartidor);
        cierre.setCajero(cajero);
        cierre.setSaldoCierre(monto);
        cierre.setMontoSolicitado(monto);
        cierre.setObservacion(limpio(request.observacion()));
        cierre = cierreRepository.save(cierre);

        TransferenciaRepartidorCajero transferencia = new TransferenciaRepartidorCajero();
        transferencia.setRepartidor(repartidor);
        transferencia.setCajero(cajero);
        transferencia.setCierre(cierre);
        transferencia.setMonto(monto);
        transferencia.setObservacion(limpio(request.observacion()));
        transferencia = transferenciaRepository.save(transferencia);
        notificarTransferencia(NotificationEventType.TRANSFERENCIA_SOLICITADA, transferencia, cajero,
                "Solicitud de caja", nombre(repartidor) + " solicita transferir " + monto + ".");
        auditoriaService.registrar("TransferenciaRepartidorCajero", transferencia.getId(),
                AuditoriaService.Accion.Crear, "Solicitó vaciado de caja por " + monto);
        return TransferenciaResponse.from(transferencia);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferenciaResponse> transferenciasPropias(String email, Pageable pageable) {
        return PageResponse.from(transferenciaRepository
                .findByRepartidorEmailOrderByFechaSolicitudDesc(email, pageable)
                .map(TransferenciaResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferenciaResponse> solicitudesCajero(String email, Pageable pageable) {
        return PageResponse.from(transferenciaRepository
                .findByCajeroEmailOrderByFechaSolicitudDesc(email, pageable)
                .map(TransferenciaResponse::from));
    }

    @Transactional
    public TransferenciaResponse aceptarTransferencia(Long id, String emailCajero) {
        TransferenciaRepartidorCajero transferencia = transferenciaParaResponder(id, emailCajero);
        Usuario repartidor = usuarioRepository.findByIdForUpdate(transferencia.getRepartidor().getId()).orElseThrow();
        BigDecimal saldo = saldo(repartidor.getId());
        if (saldo.compareTo(transferencia.getMonto().monto()) < 0) {
            throw new BusinessException("reparto.caja.saldo.insuficiente");
        }
        Usuario cajero = transferencia.getCajero();
        movimiento(repartidor, TipoMovimientoCajaRepartidor.TRANSFERENCIA_A_CAJERO,
                NaturalezaMovimiento.DEBITO, transferencia.getMonto(), "TRANSFERENCIA_REPARTIDOR",
                transferencia.getId(), cajero, "Transferencia aceptada por " + cajero.getEmail());
        cajaService.registrarIngresoAutomatico(transferencia.getMonto(),
                "Recepción de caja de " + repartidor.getEmail(), "TRR " + transferencia.getId(),
                cajero, TipoPago.Efectivo);

        transferencia.setEstado(EstadoTransferencia.ACEPTADO);
        transferencia.setFechaRespuesta(LocalDateTime.now());
        transferencia.setRespondidoPor(cajero);
        transferencia.getCierre().setEstado(EstadoTransferencia.ACEPTADO);
        cierreRepository.save(transferencia.getCierre());
        transferencia = transferenciaRepository.save(transferencia);
        notificarTransferencia(NotificationEventType.TRANSFERENCIA_ACEPTADA, transferencia, repartidor,
                "Transferencia aceptada", cajero.getEmail() + " aceptó el vaciado de caja.");
        auditoriaService.registrar("TransferenciaRepartidorCajero", transferencia.getId(),
                AuditoriaService.Accion.Pagar, "Aceptó transferencia por " + transferencia.getMonto());
        return TransferenciaResponse.from(transferencia);
    }

    @Transactional
    public TransferenciaResponse rechazarTransferencia(
            Long id, RechazarTransferenciaRequest request, String emailCajero) {
        TransferenciaRepartidorCajero transferencia = transferenciaParaResponder(id, emailCajero);
        Usuario cajero = transferencia.getCajero();
        transferencia.setEstado(EstadoTransferencia.RECHAZADO);
        transferencia.setFechaRespuesta(LocalDateTime.now());
        transferencia.setRespondidoPor(cajero);
        transferencia.setMotivoRechazo(request.motivo().trim());
        transferencia.getCierre().setEstado(EstadoTransferencia.RECHAZADO);
        cierreRepository.save(transferencia.getCierre());
        transferencia = transferenciaRepository.save(transferencia);
        notificarTransferencia(NotificationEventType.TRANSFERENCIA_RECHAZADA, transferencia,
                transferencia.getRepartidor(), "Transferencia rechazada",
                "Motivo: " + transferencia.getMotivoRechazo());
        auditoriaService.registrar("TransferenciaRepartidorCajero", transferencia.getId(),
                AuditoriaService.Accion.Editar, "Rechazó transferencia: " + transferencia.getMotivoRechazo());
        return TransferenciaResponse.from(transferencia);
    }

    private TransferenciaRepartidorCajero transferenciaParaResponder(Long id, String emailCajero) {
        TransferenciaRepartidorCajero transferencia = transferenciaRepository.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("reparto.transferencia.no.encontrada", id));
        if (!transferencia.getCajero().getEmail().equalsIgnoreCase(emailCajero)) {
            throw new AccessDeniedException("La solicitud está dirigida a otro cajero");
        }
        if (transferencia.getEstado() != EstadoTransferencia.PENDIENTE_ACEPTACION) {
            throw new BusinessException("reparto.transferencia.ya.procesada");
        }
        return transferencia;
    }

    private void validarPagoParaEntrega(AsignacionReparto asignacion) {
        Pedido pedido = asignacion.getPedido();
        if (pedido.getMetodoPago() == MetodoPagoCheckout.PAGO_EFECTIVO_REPARTIDOR
                && !cobroRepository.existsByPedidoId(pedido.getId())) {
            throw new BusinessException("reparto.entrega.cobro.requerido");
        }
        if (pedido.getEstado() != EstadoPedido.G) {
            throw new BusinessException("reparto.entrega.pago.pendiente");
        }
    }

    private Set<EstadoReparto> transiciones(EstadoReparto estado) {
        return switch (estado) {
            case ASIGNADO -> EnumSet.of(EstadoReparto.ACEPTADO);
            case ACEPTADO -> EnumSet.of(EstadoReparto.EN_RUTA);
            case EN_RUTA -> EnumSet.of(EstadoReparto.EN_DESTINO, EstadoReparto.NO_ENTREGADO);
            case EN_DESTINO -> EnumSet.of(EstadoReparto.ENTREGADO, EstadoReparto.NO_ENTREGADO);
            case ENTREGADO, NO_ENTREGADO, CANCELADO -> EnumSet.noneOf(EstadoReparto.class);
        };
    }

    private AsignacionReparto propiaParaActualizar(Long id, String email) {
        AsignacionReparto asignacion = asignacionRepository.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("reparto.asignacion.no.encontrada", id));
        if (!asignacion.getRepartidor().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("No puede modificar entregas de otro repartidor");
        }
        return asignacion;
    }

    private void historial(AsignacionReparto asignacion, EstadoReparto anterior,
                           EstadoReparto nuevo, Usuario usuario, String observacion) {
        RepartoHistorial h = new RepartoHistorial();
        h.setAsignacion(asignacion);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(nuevo);
        h.setUsuario(usuario);
        h.setObservacion(limpio(observacion));
        historialRepository.save(h);
    }

    private MovimientoCajaRepartidor movimiento(
            Usuario repartidor, TipoMovimientoCajaRepartidor tipo, NaturalezaMovimiento naturaleza,
            Dinero monto, String referenciaTipo, Long referenciaId, Usuario usuario, String observacion) {
        MovimientoCajaRepartidor movimiento = new MovimientoCajaRepartidor();
        movimiento.setRepartidor(repartidor);
        movimiento.setTipoMovimiento(tipo);
        movimiento.setNaturaleza(naturaleza);
        movimiento.setMoneda(monto.moneda());
        movimiento.setMonto(monto);
        movimiento.setReferenciaTipo(referenciaTipo);
        movimiento.setReferenciaId(referenciaId);
        movimiento.setUsuario(usuario);
        movimiento.setObservacion(observacion);
        return movimientoRepository.save(movimiento);
    }

    private BigDecimal saldo(Long repartidorId) {
        BigDecimal valor = movimientoRepository.saldo(repartidorId,
                NaturalezaMovimiento.CREDITO, EstadoMovimientoCaja.REGISTRADO);
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private RepartoResponse respuesta(AsignacionReparto asignacion) {
        return RepartoResponse.from(asignacion, cobroRepository.existsByAsignacionId(asignacion.getId()));
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
    }

    private void exigirRol(Usuario usuario, String rol) {
        if (!Boolean.TRUE.equals(usuario.getActivo()) || !rol.equals(usuario.getRol().getNombre())) {
            throw new BusinessException("usuarios.rol.invalido", rol);
        }
    }

    private void notificar(NotificationEventType tipo, AsignacionReparto asignacion,
                           Usuario destinatario, String titulo, String mensaje) {
        notificationPublisher.operacionUsuario(tipo, "REPARTO", asignacion.getId(), destinatario.getId(),
                asignacion.getPedido().getId(), asignacion.getId(), null, null, titulo, mensaje,
                "techstore://reparto/" + asignacion.getId());
    }

    private void notificarTransferencia(NotificationEventType tipo, TransferenciaRepartidorCajero transferencia,
                                        Usuario destinatario, String titulo, String mensaje) {
        notificationPublisher.operacionUsuario(tipo, "TRANSFERENCIA_REPARTIDOR", transferencia.getId(),
                destinatario.getId(), null, null, transferencia.getId(), null, titulo, mensaje,
                "techstore://transferencia/" + transferencia.getId());
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String nombre(Usuario usuario) {
        return (usuario.getNombre() + " " + usuario.getApellido()).trim();
    }
}
