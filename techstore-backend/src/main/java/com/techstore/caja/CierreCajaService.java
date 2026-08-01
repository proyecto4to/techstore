package com.techstore.caja;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.comun.AutonumeradorService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.PageResponse;
import com.techstore.facturacion.TipoPago;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Apertura y cierre de caja por turno. La apertura deja registrado con cuánto
 * efectivo arranca el cajón; el cierre arquea el efectivo físico contra lo que
 * el sistema calcula: {@code saldo inicial + ingresos - egresos}. Las
 * transferencias se informan aparte (van al banco, no a la caja física).
 *
 * <p>El turno se calcula desde el último cierre y no desde la apertura, para
 * que ningún movimiento quede fuera de todos los arqueos si se cobró algo con
 * la caja cerrada.
 */
@Service
@RequiredArgsConstructor
public class CierreCajaService {

    private static final String ENTIDAD_CIERRE = "CIERRECAJA";
    private static final String ENTIDAD_APERTURA = "APERTURACAJA";
    private static final LocalDateTime ORIGEN = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final CierreCajaRepository cierreCajaRepository;
    private final AperturaCajaRepository aperturaCajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;
    private final AuditoriaService auditoriaService;

    /** Turno abierto, o {@code abierta=false} si la caja está cerrada. */
    @Transactional(readOnly = true)
    public AperturaCajaResponse aperturaActual() {
        return aperturaCajaRepository.findByCierreIsNull()
                .map(AperturaCajaResponse::from)
                .orElseGet(AperturaCajaResponse::cerrada);
    }

    @Transactional
    public AperturaCajaResponse abrir(AbrirCajaRequest request, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        aperturaCajaRepository.findAbiertaForUpdate().ifPresent(a -> {
            throw new BusinessException(
                    "La caja ya fue abierta el %s con %s. Cerrala antes de abrir un turno nuevo."
                            .formatted(a.getFechaApertura(), a.getSaldoInicial()));
        });

        AperturaCaja apertura = new AperturaCaja();
        apertura.setNumero(autonumeradorService.siguiente(ENTIDAD_APERTURA));
        apertura.setFechaApertura(LocalDateTime.now());
        apertura.setUsuario(usuario);
        // La apertura no declara moneda: el cajón es el de la moneda del sistema.
        apertura.setSaldoInicial(Dinero.de(request.saldoInicial(), Moneda.porDefecto()));
        apertura.setNotas(request.notas());
        AperturaCaja guardada = aperturaCajaRepository.save(apertura);

        auditoriaService.registrar("AperturaCaja", guardada.getId(), AuditoriaService.Accion.Abrir,
                "Abrió la caja con " + guardada.getSaldoInicial(),
                AuditoriaService.Cambios.nuevo()
                        .valor("numero", guardada.getNumero())
                        .valor("saldoInicial", guardada.getSaldoInicial()));

        return AperturaCajaResponse.from(guardada);
    }

    @Transactional(readOnly = true)
    public PreviewCierreResponse preview() {
        LocalDateTime desde = inicioTurno();
        Dinero saldoInicial = aperturaCajaRepository.findByCierreIsNull()
                .map(AperturaCaja::getSaldoInicial)
                .orElseGet(() -> Dinero.cero(Moneda.porDefecto()));
        Totales t = calcularPendiente(saldoInicial);
        return new PreviewCierreResponse(desde,
                saldoInicial.moneda().codigo(),
                saldoInicial.monto(),
                t.ingresosEfectivo.monto(),
                t.egresosEfectivo.monto(),
                t.ingresosTransferencia.monto(),
                t.egresosTransferencia.monto(),
                t.efectivoTeorico().monto());
    }

    @Transactional
    public CierreCajaResponse cerrar(CerrarCajaRequest request, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        // Bloquea el turno: dos cierres simultáneos no pueden arquear lo mismo dos veces.
        AperturaCaja apertura = aperturaCajaRepository.findAbiertaForUpdate()
                .orElseThrow(() -> new BusinessException(
                        "No hay una caja abierta. Abrí la caja indicando el efectivo inicial antes de cerrarla."));

        Dinero saldoInicial = apertura.getSaldoInicial();
        Dinero efectivoContado = Dinero.de(request.efectivoContado(), apertura.getMoneda());
        CierreCaja cierre = new CierreCaja();
        cierre.setNumero(autonumeradorService.siguiente(ENTIDAD_CIERRE));
        cierre.setFechaDesde(inicioTurno());
        cierre.setFechaCierre(LocalDateTime.now());
        cierre.setUsuario(usuario);
        // El arqueo se hace en la moneda con la que se abrió el turno.
        cierre.setMoneda(apertura.getMoneda());
        cierre.setSaldoInicial(saldoInicial);
        cierre.setEfectivoContado(efectivoContado);
        cierre.setNotas(request.notas());
        // Totales provisorios: las columnas son NOT NULL y recién se conocen
        // después de reclamar los movimientos.
        Dinero cero = Dinero.cero(apertura.getMoneda());
        cierre.setIngresosEfectivo(cero);
        cierre.setEgresosEfectivo(cero);
        cierre.setIngresosTransferencia(cero);
        cierre.setEgresosTransferencia(cero);
        cierre.setEfectivoTeorico(saldoInicial);
        cierre.setDiferencia(efectivoContado.menos(saldoInicial));
        CierreCaja guardado = cierreCajaRepository.saveAndFlush(cierre);

        // Primero se reclaman los pendientes y después se suma lo reclamado: así lo
        // arqueado es exactamente eso, aunque entre un movimiento nuevo en el medio.
        movimientoCajaRepository.asignarPendientesA(guardado);
        Totales t = calcularDeCierre(saldoInicial, guardado.getId());

        guardado.setIngresosEfectivo(t.ingresosEfectivo);
        guardado.setEgresosEfectivo(t.egresosEfectivo);
        guardado.setIngresosTransferencia(t.ingresosTransferencia);
        guardado.setEgresosTransferencia(t.egresosTransferencia);
        guardado.setEfectivoTeorico(t.efectivoTeorico());
        guardado.setDiferencia(efectivoContado.menos(t.efectivoTeorico()));

        apertura.setCierre(guardado);
        aperturaCajaRepository.save(apertura);

        // La diferencia del arqueo es el dato que uno va a querer poder rastrear
        // meses después, así que queda registrada aparte del propio cierre.
        auditoriaService.registrar("CierreCaja", guardado.getId(), AuditoriaService.Accion.Cerrar,
                "Cerró la caja: contó %s contra %s teóricos (diferencia %s)".formatted(
                        guardado.getEfectivoContado(),
                        guardado.getEfectivoTeorico(),
                        guardado.getDiferencia()),
                AuditoriaService.Cambios.nuevo()
                        .valor("numero", guardado.getNumero())
                        .valor("saldoInicial", guardado.getSaldoInicial())
                        .valor("efectivoContado", guardado.getEfectivoContado())
                        .valor("efectivoTeorico", guardado.getEfectivoTeorico())
                        .valor("diferencia", guardado.getDiferencia()));

        return CierreCajaResponse.from(guardado);
    }

    @Transactional(readOnly = true)
    public PageResponse<CierreCajaResponse> listar(Pageable pageable) {
        return PageResponse.from(cierreCajaRepository.findAll(pageable).map(CierreCajaResponse::from));
    }

    private LocalDateTime inicioTurno() {
        return cierreCajaRepository.findTopByOrderByFechaCierreDesc()
                .map(CierreCaja::getFechaCierre)
                .orElse(ORIGEN);
    }

    /**
     * Totales de lo que todavía no arqueó ningún cierre.
     *
     * <p>Las consultas suman sin distinguir moneda, lo cual vale mientras haya
     * una sola. Con dos, un arqueo tendría que hacerse por moneda: no se puede
     * contar un cajón con guaraníes y dólares en un único total.
     */
    private Totales calcularPendiente(Dinero saldoInicial) {
        Moneda moneda = saldoInicial.moneda();
        return new Totales(saldoInicial,
                pendiente(TipoMovimiento.Ingreso, TipoPago.Efectivo, moneda),
                pendiente(TipoMovimiento.Egreso, TipoPago.Efectivo, moneda),
                pendiente(TipoMovimiento.Ingreso, TipoPago.Transferencia, moneda),
                pendiente(TipoMovimiento.Egreso, TipoPago.Transferencia, moneda));
    }

    private Dinero pendiente(TipoMovimiento tipo, TipoPago medio, Moneda moneda) {
        return Dinero.deONulo(movimientoCajaRepository.totalPendientePorTipoYMedio(tipo, medio), moneda);
    }

    /** Totales de lo que quedó asignado a un cierre. */
    private Totales calcularDeCierre(Dinero saldoInicial, Long cierreId) {
        Moneda moneda = saldoInicial.moneda();
        return new Totales(saldoInicial,
                deCierre(TipoMovimiento.Ingreso, TipoPago.Efectivo, cierreId, moneda),
                deCierre(TipoMovimiento.Egreso, TipoPago.Efectivo, cierreId, moneda),
                deCierre(TipoMovimiento.Ingreso, TipoPago.Transferencia, cierreId, moneda),
                deCierre(TipoMovimiento.Egreso, TipoPago.Transferencia, cierreId, moneda));
    }

    private Dinero deCierre(TipoMovimiento tipo, TipoPago medio, Long cierreId, Moneda moneda) {
        return Dinero.deONulo(
                movimientoCajaRepository.totalDeCierrePorTipoYMedio(tipo, medio, cierreId), moneda);
    }

    private record Totales(Dinero saldoInicial, Dinero ingresosEfectivo, Dinero egresosEfectivo,
                           Dinero ingresosTransferencia, Dinero egresosTransferencia) {
        /** Lo que debería haber en el cajón: con lo que se abrió, más lo que entró, menos lo que salió. */
        Dinero efectivoTeorico() {
            return saldoInicial.mas(ingresosEfectivo).menos(egresosEfectivo);
        }
    }
}
