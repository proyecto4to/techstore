package com.techstore.vehiculos;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.notificaciones.outbox.NotificationEventType;
import com.techstore.notificaciones.outbox.NotificationOutboxPublisher;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VehiculoService {
    private final VehiculoRepository vehiculoRepository;
    private final UsoVehiculoRepository usoRepository;
    private final EventoVehiculoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificationOutboxPublisher notificationPublisher;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<VehiculoResponse> listar(boolean admin) {
        List<Vehiculo> vehiculos = admin
                ? vehiculoRepository.findAllByOrderByMatriculaAsc()
                : vehiculoRepository.findByActivoTrueOrderByMatriculaAsc();
        Map<Long, UsoVehiculo> activos = new HashMap<>();
        usoRepository.findByEstado(EstadoUsoVehiculo.ACTIVO)
                .forEach(uso -> activos.put(uso.getVehiculo().getId(), uso));
        return vehiculos.stream().map(v -> VehiculoResponse.from(v, activos.get(v.getId()))).toList();
    }

    @Transactional
    public VehiculoResponse crear(VehiculoRequest request) {
        String matricula = normalizarMatricula(request.matricula());
        if (vehiculoRepository.existsByMatriculaIgnoreCase(matricula)) {
            throw new BusinessException("vehiculos.matricula.duplicada", matricula);
        }
        if (request.estado() == EstadoVehiculo.EN_USO) {
            throw new BusinessException("vehiculos.estado.en.uso.solo.flujo");
        }
        Vehiculo vehiculo = aplicar(new Vehiculo(), request);
        vehiculo.setMatricula(matricula);
        vehiculo = vehiculoRepository.save(vehiculo);
        auditoriaService.registrar("Vehiculo", vehiculo.getId(), AuditoriaService.Accion.Crear,
                "Alta del vehículo " + matricula);
        return VehiculoResponse.from(vehiculo, null);
    }

    @Transactional
    public VehiculoResponse actualizar(Long id, VehiculoRequest request) {
        Vehiculo vehiculo = vehiculoRepository.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("vehiculos.no.encontrado", id));
        String matricula = normalizarMatricula(request.matricula());
        if (!vehiculo.getMatricula().equalsIgnoreCase(matricula)
                && vehiculoRepository.existsByMatriculaIgnoreCase(matricula)) {
            throw new BusinessException("vehiculos.matricula.duplicada", matricula);
        }
        UsoVehiculo uso = usoRepository.findByEstado(EstadoUsoVehiculo.ACTIVO).stream()
                .filter(u -> u.getVehiculo().getId().equals(id)).findFirst().orElse(null);
        if (uso != null && request.estado() != EstadoVehiculo.EN_USO) {
            throw new BusinessException("vehiculos.en.uso.no.editable");
        }
        BigDecimal kmAnterior = vehiculo.getKilometrajeActual();
        if (request.kilometrajeActual().compareTo(kmAnterior) < 0) {
            throw new BusinessException("vehiculos.km.no.retrocede", kmAnterior);
        }
        EstadoVehiculo estadoAnterior = vehiculo.getEstado();
        aplicar(vehiculo, request);
        vehiculo.setMatricula(matricula);
        vehiculo = vehiculoRepository.save(vehiculo);
        auditoriaService.registrar("Vehiculo", id, AuditoriaService.Accion.Editar,
                "Actualizó el vehículo " + matricula,
                AuditoriaService.Cambios.nuevo().de("estado", estadoAnterior, vehiculo.getEstado())
                        .de("kilometraje", kmAnterior, vehiculo.getKilometrajeActual()));
        return VehiculoResponse.from(vehiculo, uso);
    }

    @Transactional
    public VehiculoResponse cambiarActivo(Long id, boolean activo) {
        Vehiculo vehiculo = vehiculoRepository.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("vehiculos.no.encontrado", id));
        if (!activo && usoRepository.existsByVehiculoIdAndEstado(id, EstadoUsoVehiculo.ACTIVO)) {
            throw new BusinessException("vehiculos.en.uso.no.desactivar");
        }
        vehiculo.setActivo(activo);
        if (!activo) vehiculo.setEstado(EstadoVehiculo.INACTIVO);
        if (activo && vehiculo.getEstado() == EstadoVehiculo.INACTIVO) {
            vehiculo.setEstado(EstadoVehiculo.DISPONIBLE);
        }
        vehiculo = vehiculoRepository.save(vehiculo);
        auditoriaService.registrar("Vehiculo", id, AuditoriaService.Accion.Editar,
                (activo ? "Activó" : "Desactivó") + " el vehículo " + vehiculo.getMatricula());
        return VehiculoResponse.from(vehiculo, null);
    }

    @Transactional
    public UsoVehiculoResponse iniciarUso(Long vehiculoId, IniciarUsoVehiculoRequest request, String email) {
        Usuario repartidorSinLock = usuario(email);
        Usuario repartidor = usuarioRepository.findByIdForUpdate(repartidorSinLock.getId()).orElseThrow();
        exigirRepartidor(repartidor);
        if (usoRepository.existsByRepartidorIdAndEstado(repartidor.getId(), EstadoUsoVehiculo.ACTIVO)) {
            throw new BusinessException("vehiculos.repartidor.ya.tiene.activo");
        }
        Vehiculo vehiculo = vehiculoRepository.findForUpdateById(vehiculoId)
                .orElseThrow(() -> new ResourceNotFoundException("vehiculos.no.encontrado", vehiculoId));
        if (!Boolean.TRUE.equals(vehiculo.getActivo()) || vehiculo.getEstado() != EstadoVehiculo.DISPONIBLE
                || usoRepository.existsByVehiculoIdAndEstado(vehiculoId, EstadoUsoVehiculo.ACTIVO)) {
            throw new BusinessException("vehiculos.no.disponible");
        }
        if (request.kilometrajeInicial().compareTo(vehiculo.getKilometrajeActual()) < 0) {
            throw new BusinessException("vehiculos.km.inicial.invalido", vehiculo.getKilometrajeActual());
        }
        UsoVehiculo uso = new UsoVehiculo();
        uso.setVehiculo(vehiculo);
        uso.setRepartidor(repartidor);
        uso.setKmInicio(request.kilometrajeInicial());
        uso.setCombustibleInicio(request.combustibleInicial());
        uso.setObservacionInicio(limpio(request.observacion()));
        try {
            uso = usoRepository.saveAndFlush(uso);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("vehiculos.no.disponible");
        }
        vehiculo.setEstado(EstadoVehiculo.EN_USO);
        vehiculo.setKilometrajeActual(request.kilometrajeInicial());
        vehiculoRepository.save(vehiculo);
        notificationPublisher.operacionUsuario(NotificationEventType.VEHICULO_ASIGNADO,
                "VEHICULO", vehiculo.getId(), repartidor.getId(), null, null, null, vehiculo.getId(),
                "Vehículo en uso", "Iniciaste el uso de " + vehiculo.getMatricula() + ".",
                "techstore://vehiculo/" + vehiculo.getId());
        auditoriaService.registrar("UsoVehiculo", uso.getId(), AuditoriaService.Accion.Abrir,
                repartidor.getEmail() + " inició " + vehiculo.getMatricula(),
                AuditoriaService.Cambios.nuevo().valor("kmInicio", request.kilometrajeInicial()));
        return UsoVehiculoResponse.from(uso);
    }

    @Transactional(readOnly = true)
    public UsoVehiculoResponse usoActual(String email) {
        return usoRepository.findByRepartidorEmailAndEstado(email, EstadoUsoVehiculo.ACTIVO)
                .map(UsoVehiculoResponse::from).orElse(null);
    }

    @Transactional
    public EventoVehiculoResponse registrarEvento(EventoVehiculoRequest request, String email) {
        UsoVehiculo uso = usoRepository.findOwnForUpdate(email, EstadoUsoVehiculo.ACTIVO)
                .orElseThrow(() -> new BusinessException("vehiculos.repartidor.sin.uso"));
        if (request.kilometraje().compareTo(uso.getKmInicio()) < 0) {
            throw new BusinessException("vehiculos.evento.km.invalido", uso.getKmInicio());
        }
        Map<String, Object> datos = request.datosAdicionales() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(request.datosAdicionales());
        validarDatos(request.tipo(), datos);

        EventoVehiculo evento = new EventoVehiculo();
        evento.setUsoVehiculo(uso);
        evento.setVehiculo(uso.getVehiculo());
        evento.setRepartidor(uso.getRepartidor());
        evento.setTipoEvento(request.tipo());
        evento.setKilometraje(request.kilometraje());
        evento.setDescripcion(request.descripcion().trim());
        evento.setUbicacion(limpio(request.ubicacion()));
        evento.setImporte(request.importe());
        evento.setDatosAdicionales(datos);
        evento.setObservacion(limpio(request.observacion()));

        boolean puedeContinuar = !Boolean.FALSE.equals(datos.get("puedeContinuar"));
        boolean problema = Set.of(TipoEventoVehiculo.AVERIA, TipoEventoVehiculo.ESTANCAMIENTO,
                TipoEventoVehiculo.ACCIDENTE, TipoEventoVehiculo.MANTENIMIENTO).contains(request.tipo());
        boolean requiereAtencion = problema || Boolean.TRUE.equals(request.enviarAMantenimiento());
        evento.setRequiereAtencion(requiereAtencion);
        evento = eventoRepository.save(evento);

        Vehiculo vehiculo = uso.getVehiculo();
        if (request.tipo() == TipoEventoVehiculo.ACCIDENTE || !puedeContinuar) {
            vehiculo.setEstado(EstadoVehiculo.FUERA_DE_SERVICIO);
        } else if (requiereAtencion) {
            vehiculo.setEstado(EstadoVehiculo.MANTENIMIENTO);
        }
        vehiculoRepository.save(vehiculo);

        if (requiereAtencion) {
            for (Usuario admin : usuarioRepository.findByRolNombreInAndActivoTrue(List.of("Admin"))) {
                notificationPublisher.operacionUsuario(NotificationEventType.VEHICULO_PROBLEMA,
                        "EVENTO_VEHICULO", evento.getId(), admin.getId(), null, null, null, vehiculo.getId(),
                        "Problema con vehículo " + vehiculo.getMatricula(),
                        request.tipo().name() + ": " + request.descripcion().trim(),
                        "techstore://vehiculo/" + vehiculo.getId());
            }
        }
        auditoriaService.registrar("EventoVehiculo", evento.getId(), AuditoriaService.Accion.Crear,
                request.tipo() + " en " + vehiculo.getMatricula(),
                AuditoriaService.Cambios.nuevo().valor("kilometraje", request.kilometraje())
                        .valor("requiereAtencion", requiereAtencion));
        return EventoVehiculoResponse.from(evento);
    }

    @Transactional
    public UsoVehiculoResponse finalizarUso(FinalizarUsoVehiculoRequest request, String email) {
        UsoVehiculo uso = usoRepository.findOwnForUpdate(email, EstadoUsoVehiculo.ACTIVO)
                .orElseThrow(() -> new BusinessException("vehiculos.repartidor.sin.uso"));
        if (request.kilometrajeFinal().compareTo(uso.getKmInicio()) < 0) {
            throw new BusinessException("vehiculos.km.final.invalido", uso.getKmInicio());
        }
        Long usoId = uso.getId();
        BigDecimal maxEvento = eventoRepository.findByVehiculoIdOrderByFechaDescIdDesc(uso.getVehiculo().getId())
                .stream().filter(e -> e.getUsoVehiculo().getId().equals(usoId))
                .map(EventoVehiculo::getKilometraje).max(BigDecimal::compareTo).orElse(uso.getKmInicio());
        if (request.kilometrajeFinal().compareTo(maxEvento) < 0) {
            throw new BusinessException("vehiculos.km.final.menor.evento", maxEvento);
        }
        uso.setKmFin(request.kilometrajeFinal());
        uso.setCombustibleFin(request.combustibleFinal());
        uso.setFechaFin(LocalDateTime.now());
        uso.setObservacionFin(limpio(request.observacion()));
        uso.setEstado(EstadoUsoVehiculo.FINALIZADO);

        Vehiculo vehiculo = uso.getVehiculo();
        vehiculo.setKilometrajeActual(request.kilometrajeFinal());
        boolean pendiente = eventoRepository
                .existsByVehiculoIdAndRequiereAtencionTrueAndResueltoFalse(vehiculo.getId());
        if (!pendiente && vehiculo.getEstado() == EstadoVehiculo.EN_USO) {
            vehiculo.setEstado(EstadoVehiculo.DISPONIBLE);
        }
        vehiculoRepository.save(vehiculo);
        uso = usoRepository.save(uso);
        auditoriaService.registrar("UsoVehiculo", uso.getId(), AuditoriaService.Accion.Cerrar,
                email + " dejó " + vehiculo.getMatricula(),
                AuditoriaService.Cambios.nuevo().valor("kmFin", request.kilometrajeFinal())
                        .valor("estadoVehiculo", vehiculo.getEstado()));
        return UsoVehiculoResponse.from(uso);
    }

    @Transactional(readOnly = true)
    public HistorialVehiculoResponse historial(Long id) {
        Vehiculo vehiculo = vehiculoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("vehiculos.no.encontrado", id));
        UsoVehiculo activo = usoRepository.findByEstado(EstadoUsoVehiculo.ACTIVO).stream()
                .filter(u -> u.getVehiculo().getId().equals(id)).findFirst().orElse(null);
        return new HistorialVehiculoResponse(VehiculoResponse.from(vehiculo, activo),
                usoRepository.findByVehiculoIdOrderByFechaInicioDesc(id).stream()
                        .map(UsoVehiculoResponse::from).toList(),
                eventoRepository.findByVehiculoIdOrderByFechaDescIdDesc(id).stream()
                        .map(EventoVehiculoResponse::from).toList());
    }

    @Transactional
    public EventoVehiculoResponse resolverEvento(Long id, ResolverEventoVehiculoRequest request) {
        EventoVehiculo evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("vehiculos.evento.no.encontrado", id));
        if (request.estadoVehiculo() == EstadoVehiculo.EN_USO
                || request.estadoVehiculo() == EstadoVehiculo.INACTIVO) {
            throw new BusinessException("vehiculos.estado.resolucion.invalido");
        }
        evento.setResuelto(true);
        if (limpio(request.observacion()) != null) evento.setObservacion(limpio(request.observacion()));
        Vehiculo vehiculo = evento.getVehiculo();
        boolean tieneUso = usoRepository.existsByVehiculoIdAndEstado(vehiculo.getId(), EstadoUsoVehiculo.ACTIVO);
        vehiculo.setEstado(tieneUso ? EstadoVehiculo.EN_USO : request.estadoVehiculo());
        vehiculoRepository.save(vehiculo);
        evento = eventoRepository.save(evento);
        auditoriaService.registrar("EventoVehiculo", evento.getId(), AuditoriaService.Accion.Editar,
                "Resolvió evento y dejó vehículo en " + vehiculo.getEstado());
        return EventoVehiculoResponse.from(evento);
    }

    private Vehiculo aplicar(Vehiculo vehiculo, VehiculoRequest r) {
        vehiculo.setMarca(r.marca().trim());
        vehiculo.setModelo(r.modelo().trim());
        vehiculo.setAnio(r.anio());
        vehiculo.setTipo(r.tipo().trim());
        vehiculo.setColor(limpio(r.color()));
        vehiculo.setNumeroChasis(limpio(r.numeroChasis()));
        vehiculo.setKilometrajeActual(r.kilometrajeActual());
        vehiculo.setTipoCombustible(limpio(r.tipoCombustible()));
        vehiculo.setEstado(r.estado());
        vehiculo.setObservaciones(limpio(r.observaciones()));
        vehiculo.setActivo(r.estado() != EstadoVehiculo.INACTIVO);
        return vehiculo;
    }

    private void validarDatos(TipoEventoVehiculo tipo, Map<String, Object> datos) {
        switch (tipo) {
            case RECARGA_COMBUSTIBLE -> requerir(datos,
                    "tipoCombustible", "cantidad", "unidad", "nivelCombustiblePosterior");
            case REPARACION -> requerir(datos, "problema", "reparacionRealizada", "tallerProveedor");
            case AVERIA, ESTANCAMIENTO -> requerir(datos, "severidad", "puedeContinuar");
            case ACCIDENTE -> requerir(datos, "danosObservados");
            default -> { }
        }
    }

    private void requerir(Map<String, Object> datos, String... campos) {
        for (String campo : campos) {
            Object valor = datos.get(campo);
            if (valor == null || (valor instanceof String texto && texto.isBlank())) {
                throw new BusinessException("vehiculos.evento.dato.requerido", campo);
            }
        }
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
    }

    private void exigirRepartidor(Usuario usuario) {
        if (!Boolean.TRUE.equals(usuario.getActivo())
                || !"Repartidor".equals(usuario.getRol().getNombre())) {
            throw new BusinessException("usuarios.rol.invalido", "Repartidor");
        }
    }

    private static String normalizarMatricula(String valor) {
        return valor.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
