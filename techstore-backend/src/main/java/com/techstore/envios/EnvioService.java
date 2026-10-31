package com.techstore.envios;

import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.direcciones.DireccionEntrega;
import com.techstore.direcciones.DireccionEntregaService;
import com.techstore.notificaciones.outbox.NotificationEventType;
import com.techstore.notificaciones.outbox.NotificationOutboxPublisher;
import com.techstore.reparto.EstadoReparto;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.MetodoEntrega;
import com.techstore.ventas.Pedido;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EnvioService {
    private final List<ShippingProvider> providers;
    private final EnvioRepository envioRepository;
    private final TarifaEnvioRepository tarifaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final DireccionEntregaService direccionService;
    private final NotificationOutboxPublisher notificationPublisher;

    @Transactional(readOnly = true)
    public List<CotizacionEnvioResponse> cotizar(String email, CotizarEnvioRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Usuario no encontrado"));
        DireccionEntrega direccion = request.direccionEntregaId() == null ? null
                : direccionService.buscarPropia(usuario, request.direccionEntregaId());
        Map<Producto, Integer> items = cargarItems(request.items());
        Dinero subtotal = items.entrySet().stream()
                .map(e -> e.getKey().getPrecio().por(e.getValue()))
                .reduce(Dinero.cero(Moneda.porDefecto()), Dinero::mas);
        return cotizaciones(direccion, items, subtotal).stream().map(CotizacionEnvioResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public CotizacionEnvio seleccionar(DireccionEntrega direccion, Map<Producto, Integer> items,
                                       Dinero subtotal, Long tarifaId, MetodoEntrega metodo) {
        List<CotizacionEnvio> disponibles = cotizaciones(direccion, items, subtotal);
        if (tarifaId == null && metodo == MetodoEntrega.RETIRO_TIENDA) {
            return disponibles.stream().filter(c -> c.tipo() == TipoEnvio.RETIRO_TIENDA).findFirst()
                    .orElseThrow(() -> new BusinessException("envios.tarifa.retiro.no.disponible"));
        }
        CotizacionEnvio elegida = disponibles.stream().filter(c -> c.tarifaId().equals(tarifaId)).findFirst()
                .orElseThrow(() -> new BusinessException("envios.tarifa.no.disponible"));
        boolean retiro = elegida.tipo() == TipoEnvio.RETIRO_TIENDA;
        if ((metodo == MetodoEntrega.RETIRO_TIENDA) != retiro) {
            throw new BusinessException("envios.tarifa.metodo.incompatible");
        }
        return elegida;
    }

    @Transactional
    public Envio registrar(Pedido pedido, DireccionEntrega direccion, CotizacionEnvio c) {
        Envio envio = new Envio();
        envio.setPedido(pedido);
        envio.setDireccionEntrega(direccion);
        envio.setTarifa(tarifaRepository.findById(c.tarifaId()).orElse(null));
        envio.setTipo(c.tipo());
        envio.setProviderCode(c.providerCode());
        envio.setTransportista(c.transportista());
        envio.setCodigoSeguimiento("TS-" + pedido.getNumero());
        envio.setMoneda(c.costo().moneda());
        envio.setCosto(c.costo());
        envio.setIva(c.iva());
        envio.setFechaEstimadaDesde(c.fechaEstimadaDesde());
        envio.setFechaEstimadaHasta(c.fechaEstimadaHasta());
        envio.setEstado(EstadoEnvio.PENDIENTE);
        agregarEvento(envio, EstadoEnvio.PENDIENTE, "Pedido recibido y envío registrado", null);
        return envioRepository.save(envio);
    }

    @Transactional
    public void pedidoConfirmado(Pedido pedido) {
        envioRepository.findByPedidoId(pedido.getId()).ifPresent(envio -> {
            if (envio.getEstado() == EstadoEnvio.PENDIENTE) {
                cambiarEstado(envio, EstadoEnvio.PREPARANDO, "Pedido confirmado; preparación iniciada", null);
            }
        });
    }

    @Transactional
    public void pedidoCancelado(Pedido pedido) {
        envioRepository.findByPedidoId(pedido.getId()).ifPresent(envio -> {
            if (envio.getEstado() != EstadoEnvio.ENTREGADO && envio.getEstado() != EstadoEnvio.CANCELADO) {
                cambiarEstado(envio, EstadoEnvio.CANCELADO, "Pedido cancelado", null);
            }
        });
    }

    /** Mantiene el tracking existente sincronizado con el flujo del repartidor. */
    @Transactional
    public void reflejarReparto(Pedido pedido, EstadoReparto estado, String observacion) {
        Envio envio = buscarPorPedido(pedido.getId());
        String detalle = observacion == null || observacion.isBlank()
                ? "Actualización del repartidor: " + estado.name()
                : observacion.trim();
        switch (estado) {
            case ASIGNADO, ACEPTADO -> avanzarHastaListo(envio);
            case EN_RUTA -> {
                avanzarHastaListo(envio);
                if (envio.getEstado() == EstadoEnvio.LISTO_PARA_ENVIO) {
                    cambiarEstado(envio, EstadoEnvio.DESPACHADO, "El repartidor inició la entrega", null);
                }
                if (envio.getEstado() == EstadoEnvio.DESPACHADO) {
                    cambiarEstado(envio, EstadoEnvio.EN_CAMINO, detalle, null);
                }
            }
            case EN_DESTINO -> {
                reflejarReparto(pedido, EstadoReparto.EN_RUTA, null);
                if (envio.getEstado() == EstadoEnvio.EN_CAMINO) {
                    agregarEvento(envio, EstadoEnvio.EN_CAMINO, detalle, null);
                }
            }
            case ENTREGADO -> {
                reflejarReparto(pedido, EstadoReparto.EN_RUTA, null);
                if (envio.getEstado() == EstadoEnvio.EN_CAMINO
                        || envio.getEstado() == EstadoEnvio.INTENTO_ENTREGA) {
                    cambiarEstado(envio, EstadoEnvio.ENTREGADO, detalle, null);
                }
            }
            case NO_ENTREGADO -> {
                reflejarReparto(pedido, EstadoReparto.EN_RUTA, null);
                if (envio.getEstado() == EstadoEnvio.EN_CAMINO) {
                    cambiarEstado(envio, EstadoEnvio.INTENTO_ENTREGA, detalle, null);
                }
            }
            case CANCELADO -> {
                if (envio.getEstado() != EstadoEnvio.ENTREGADO
                        && envio.getEstado() != EstadoEnvio.CANCELADO) {
                    cambiarEstado(envio, EstadoEnvio.CANCELADO, detalle, null);
                }
            }
        }
        envioRepository.save(envio);
    }

    private void avanzarHastaListo(Envio envio) {
        if (envio.getEstado() == EstadoEnvio.PENDIENTE) {
            cambiarEstado(envio, EstadoEnvio.PREPARANDO, "Pedido confirmado; preparación iniciada", null);
        }
        if (envio.getEstado() == EstadoEnvio.PREPARANDO) {
            cambiarEstado(envio, EstadoEnvio.LISTO_PARA_ENVIO, "Pedido listo para el repartidor", null);
        }
    }

    @Transactional(readOnly = true)
    public EnvioResponse tracking(Long pedidoId, String email, boolean staff) {
        Envio envio = buscarPorPedido(pedidoId);
        if (!staff && !envio.getPedido().getUsuario().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("No puede consultar envíos de otro usuario");
        }
        return respuesta(envio);
    }

    /**
     * Mueve el envío a mano, desde la administración o el mostrador.
     *
     * <p>Antes no miraba el pedido: se podía poner «en preparación» o
     * «entregado» un pedido que nadie había confirmado ni cobrado. Ahora el envío
     * avanza solo detrás del pedido: para prepararlo o despacharlo tiene que estar
     * confirmado —lo que, si se paga por transferencia, quiere decir con el pago
     * verificado— y para darlo por entregado tiene que estar cobrado. Al
     * entregarlo, el pedido queda cerrado: era lo que le faltaba al retiro en el
     * local, que no pasa por un repartidor.
     */
    @Transactional
    public EnvioResponse actualizar(Long pedidoId, ActualizarEnvioRequest request) {
        Envio envio = buscarPorPedido(pedidoId);
        Pedido pedido = envio.getPedido();
        EstadoEnvio nuevo = request.estado();
        if (nuevo != EstadoEnvio.CANCELADO && nuevo != EstadoEnvio.PENDIENTE && nuevo != envio.getEstado()) {
            boolean confirmado = pedido.getEstado() == EstadoPedido.C
                    || pedido.getEstado() == EstadoPedido.G
                    || pedido.getEstado() == EstadoPedido.N;
            if (!confirmado || (pedido.getEstadoPago() != null && pedido.getEstadoPago().pendiente())) {
                throw new BusinessException("envios.pedido.sin.confirmar", pedido.getNumero());
            }
            if (nuevo == EstadoEnvio.ENTREGADO && pedido.getEstado() == EstadoPedido.C) {
                throw new BusinessException("envios.entrega.sin.cobro", pedido.getNumero());
            }
        }
        if (request.transportista() != null) envio.setTransportista(request.transportista().trim());
        if (request.codigoSeguimiento() != null) envio.setCodigoSeguimiento(request.codigoSeguimiento().trim());
        String descripcion = request.descripcion() == null || request.descripcion().isBlank()
                ? descripcionPorDefecto(nuevo) : request.descripcion().trim();
        cambiarEstado(envio, nuevo, descripcion, request.ubicacion());
        if (nuevo == EstadoEnvio.ENTREGADO && pedido.getEstado() == EstadoPedido.G) {
            pedido.setEstado(EstadoPedido.N);
        }
        return respuesta(envioRepository.save(envio));
    }

    @Transactional(readOnly = true)
    public List<TarifaEnvioResponse> listarTarifas() {
        return tarifaRepository.findAll().stream()
                .sorted(Comparator.comparing(TarifaEnvio::getPrioridad).thenComparing(TarifaEnvio::getId))
                .map(this::respuestaTarifa).toList();
    }

    @Transactional
    public TarifaEnvioResponse crearTarifa(TarifaEnvioRequest request) {
        validarRangos(request);
        return respuestaTarifa(tarifaRepository.save(aplicar(new TarifaEnvio(), request)));
    }

    @Transactional
    public TarifaEnvioResponse actualizarTarifa(Long id, TarifaEnvioRequest request) {
        validarRangos(request);
        TarifaEnvio tarifa = tarifaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("envios.tarifa.no.encontrada", id));
        return respuestaTarifa(tarifaRepository.save(aplicar(tarifa, request)));
    }

    private List<CotizacionEnvio> cotizaciones(DireccionEntrega direccion, Map<Producto, Integer> items, Dinero subtotal) {
        return providers.stream().flatMap(p -> p.cotizar(direccion, items, subtotal).stream()).toList();
    }

    private Map<Producto, Integer> cargarItems(List<CotizarEnvioRequest.Item> lineas) {
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        lineas.forEach(i -> cantidades.merge(i.productoId(), i.cantidad(), Integer::sum));
        Map<Long, Producto> productos = new HashMap<>();
        productoRepository.findAllById(cantidades.keySet()).forEach(p -> productos.put(p.getId(), p));
        Map<Producto, Integer> resultado = new LinkedHashMap<>();
        cantidades.forEach((id, cantidad) -> {
            Producto p = productos.get(id);
            if (p == null || !Boolean.TRUE.equals(p.getActivo())) {
                throw new ResourceNotFoundException("catalogo.producto.no.encontrado", id);
            }
            resultado.put(p, cantidad);
        });
        return resultado;
    }

    private Envio buscarPorPedido(Long pedidoId) {
        return envioRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("envios.pedido.sin.envio", pedidoId));
    }

    private void cambiarEstado(Envio envio, EstadoEnvio nuevo, String descripcion, String ubicacion) {
        if (envio.getEstado() == nuevo) return;
        if (!transiciones(envio.getEstado()).contains(nuevo)) {
            throw new BusinessException("envios.estado.transicion.invalida", envio.getEstado(), nuevo);
        }
        envio.setEstado(nuevo);
        LocalDateTime ahora = LocalDateTime.now();
        if (nuevo == EstadoEnvio.DESPACHADO && envio.getFechaDespacho() == null) envio.setFechaDespacho(ahora);
        if (nuevo == EstadoEnvio.ENTREGADO) envio.setFechaEntrega(ahora);
        agregarEvento(envio, nuevo, descripcion, ubicacion);
        switch (nuevo) {
            case LISTO_PARA_ENVIO -> {
                if (envio.getTipo() == TipoEnvio.RETIRO_TIENDA) {
                    notificationPublisher.envioActualizado(NotificationEventType.PEDIDO_LISTO_RETIRO, envio);
                }
            }
            case PREPARANDO -> notificationPublisher.envioActualizado(
                    NotificationEventType.PEDIDO_PREPARADO, envio);
            case DESPACHADO -> notificationPublisher.envioActualizado(
                    NotificationEventType.ENVIO_DESPACHADO, envio);
            case EN_CAMINO -> notificationPublisher.envioActualizado(
                    NotificationEventType.ENVIO_EN_CAMINO, envio);
            case ENTREGADO -> notificationPublisher.envioActualizado(
                    NotificationEventType.ENVIO_ENTREGADO, envio);
            default -> { }
        }
    }

    private Set<EstadoEnvio> transiciones(EstadoEnvio estado) {
        return switch (estado) {
            case PENDIENTE -> EnumSet.of(EstadoEnvio.PREPARANDO, EstadoEnvio.CANCELADO);
            case PREPARANDO -> EnumSet.of(EstadoEnvio.LISTO_PARA_ENVIO, EstadoEnvio.CANCELADO);
            case LISTO_PARA_ENVIO -> EnumSet.of(EstadoEnvio.DESPACHADO, EstadoEnvio.ENTREGADO, EstadoEnvio.CANCELADO);
            case DESPACHADO -> EnumSet.of(EstadoEnvio.EN_CAMINO, EstadoEnvio.ENTREGADO, EstadoEnvio.CANCELADO);
            case EN_CAMINO -> EnumSet.of(EstadoEnvio.INTENTO_ENTREGA, EstadoEnvio.ENTREGADO, EstadoEnvio.CANCELADO);
            case INTENTO_ENTREGA -> EnumSet.of(EstadoEnvio.EN_CAMINO, EstadoEnvio.ENTREGADO, EstadoEnvio.CANCELADO);
            case ENTREGADO, CANCELADO -> EnumSet.noneOf(EstadoEnvio.class);
        };
    }

    private void agregarEvento(Envio envio, EstadoEnvio estado, String descripcion, String ubicacion) {
        EnvioEvento evento = new EnvioEvento();
        evento.setEstado(estado);
        evento.setDescripcion(descripcion);
        evento.setUbicacion(ubicacion == null || ubicacion.isBlank() ? null : ubicacion.trim());
        envio.agregarEvento(evento);
    }

    private String descripcionPorDefecto(EstadoEnvio estado) {
        return switch (estado) {
            case PENDIENTE -> "Envío pendiente";
            case PREPARANDO -> "Pedido en preparación";
            case LISTO_PARA_ENVIO -> "Pedido listo para enviar o retirar";
            case DESPACHADO -> "Pedido despachado";
            case EN_CAMINO -> "Pedido en camino";
            case INTENTO_ENTREGA -> "Se registró un intento de entrega";
            case ENTREGADO -> "Pedido entregado";
            case CANCELADO -> "Envío cancelado";
        };
    }

    private EnvioResponse respuesta(Envio e) {
        return new EnvioResponse(e.getId(), e.getPedido().getId(), e.getPedido().getNumero(), e.getTipo().name(),
                e.getProviderCode(), e.getTransportista(), e.getCodigoSeguimiento(), e.getCosto().monto(),
                e.getMoneda().codigo(), e.getIva(), e.getFechaEstimadaDesde(), e.getFechaEstimadaHasta(),
                e.getFechaDespacho(), e.getFechaEntrega(), e.getEstado().name(), e.getObservaciones(),
                e.getEventos().stream().map(v -> new EnvioResponse.EventoResponse(v.getId(), v.getEstado().name(),
                        v.getDescripcion(), v.getFechaHora(), v.getUbicacion())).toList());
    }

    private void validarRangos(TarifaEnvioRequest r) {
        if ((r.pesoMinKg() != null && r.pesoMaxKg() != null && r.pesoMaxKg().compareTo(r.pesoMinKg()) < 0)
                || (r.subtotalMin() != null && r.subtotalMax() != null && r.subtotalMax().compareTo(r.subtotalMin()) < 0)
                || r.diasEstimadosMax() < r.diasEstimadosMin()
                || (r.vigenciaDesde() != null && r.vigenciaHasta() != null && r.vigenciaHasta().isBefore(r.vigenciaDesde()))) {
            throw new BusinessException("envios.tarifa.rango.invalido");
        }
        if (!Set.of(0, 5, 10).contains(r.iva())) throw new BusinessException("envios.tarifa.iva.invalido");
    }

    private TarifaEnvio aplicar(TarifaEnvio t, TarifaEnvioRequest r) {
        t.setCodigo(r.codigo().trim()); t.setNombre(r.nombre().trim()); t.setDescripcion(r.descripcion());
        t.setTipo(r.tipo()); t.setProviderCode(r.providerCode().trim()); t.setTransportista(r.transportista());
        t.setDepartamento(r.departamento()); t.setCiudad(r.ciudad()); t.setZona(r.zona());
        t.setPesoMinKg(r.pesoMinKg()); t.setPesoMaxKg(r.pesoMaxKg());
        t.setSubtotalMin(r.subtotalMin()); t.setSubtotalMax(r.subtotalMax());
        t.setCosto(Dinero.de(r.costo(), Moneda.porDefecto())); t.setUmbralEnvioGratis(r.umbralEnvioGratis());
        t.setIva(BigDecimal.valueOf(r.iva())); t.setDiasEstimadosMin(r.diasEstimadosMin());
        t.setDiasEstimadosMax(r.diasEstimadosMax()); t.setPrioridad(r.prioridad());
        t.setPromocion(Boolean.TRUE.equals(r.promocion())); t.setVigenciaDesde(r.vigenciaDesde());
        t.setVigenciaHasta(r.vigenciaHasta()); t.setActiva(r.activa() == null || r.activa());
        return t;
    }

    private TarifaEnvioResponse respuestaTarifa(TarifaEnvio t) {
        return new TarifaEnvioResponse(t.getId(), t.getCodigo(), t.getNombre(), t.getDescripcion(), t.getTipo().name(),
                t.getProviderCode(), t.getTransportista(), t.getDepartamento(), t.getCiudad(), t.getZona(),
                t.getPesoMinKg(), t.getPesoMaxKg(), t.getSubtotalMin(), t.getSubtotalMax(), t.getCosto().monto(),
                t.getUmbralEnvioGratis(), t.getIva(), t.getDiasEstimadosMin(), t.getDiasEstimadosMax(),
                t.getPrioridad(), t.getPromocion(), t.getVigenciaDesde(), t.getVigenciaHasta(), t.getActiva());
    }
}
