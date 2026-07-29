package com.techstore.service;

import com.techstore.dto.request.AbrirCajaRequest;
import com.techstore.dto.request.CerrarCajaRequest;
import com.techstore.dto.response.AperturaCajaResponse;
import com.techstore.dto.response.CierreCajaResponse;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.PreviewCierreResponse;
import com.techstore.entity.AperturaCaja;
import com.techstore.entity.CierreCaja;
import com.techstore.entity.TipoMovimiento;
import com.techstore.entity.TipoPago;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.repository.AperturaCajaRepository;
import com.techstore.repository.CierreCajaRepository;
import com.techstore.repository.MovimientoCajaRepository;
import com.techstore.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
                    "La caja ya fue abierta el %s con Gs. %s. Cerrala antes de abrir un turno nuevo."
                            .formatted(a.getFechaApertura(), a.getSaldoInicial().toPlainString()));
        });

        AperturaCaja apertura = new AperturaCaja();
        apertura.setNumero(autonumeradorService.siguiente(ENTIDAD_APERTURA));
        apertura.setFechaApertura(LocalDateTime.now());
        apertura.setUsuario(usuario);
        apertura.setSaldoInicial(request.saldoInicial());
        apertura.setNotas(request.notas());
        return AperturaCajaResponse.from(aperturaCajaRepository.save(apertura));
    }

    @Transactional(readOnly = true)
    public PreviewCierreResponse preview() {
        LocalDateTime desde = inicioTurno();
        BigDecimal saldoInicial = aperturaCajaRepository.findByCierreIsNull()
                .map(AperturaCaja::getSaldoInicial)
                .orElse(BigDecimal.ZERO);
        Totales t = calcularPendiente(saldoInicial);
        return new PreviewCierreResponse(desde, saldoInicial, t.ingresosEfectivo, t.egresosEfectivo,
                t.ingresosTransferencia, t.egresosTransferencia, t.efectivoTeorico());
    }

    @Transactional
    public CierreCajaResponse cerrar(CerrarCajaRequest request, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        // Bloquea el turno: dos cierres simultáneos no pueden arquear lo mismo dos veces.
        AperturaCaja apertura = aperturaCajaRepository.findAbiertaForUpdate()
                .orElseThrow(() -> new BusinessException(
                        "No hay una caja abierta. Abrí la caja indicando el efectivo inicial antes de cerrarla."));

        BigDecimal saldoInicial = apertura.getSaldoInicial();
        CierreCaja cierre = new CierreCaja();
        cierre.setNumero(autonumeradorService.siguiente(ENTIDAD_CIERRE));
        cierre.setFechaDesde(inicioTurno());
        cierre.setFechaCierre(LocalDateTime.now());
        cierre.setUsuario(usuario);
        cierre.setSaldoInicial(saldoInicial);
        cierre.setEfectivoContado(request.efectivoContado());
        cierre.setNotas(request.notas());
        // Totales provisorios: las columnas son NOT NULL y recién se conocen
        // después de reclamar los movimientos.
        cierre.setIngresosEfectivo(BigDecimal.ZERO);
        cierre.setEgresosEfectivo(BigDecimal.ZERO);
        cierre.setIngresosTransferencia(BigDecimal.ZERO);
        cierre.setEgresosTransferencia(BigDecimal.ZERO);
        cierre.setEfectivoTeorico(saldoInicial);
        cierre.setDiferencia(request.efectivoContado().subtract(saldoInicial));
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
        guardado.setDiferencia(request.efectivoContado().subtract(t.efectivoTeorico()));

        apertura.setCierre(guardado);
        aperturaCajaRepository.save(apertura);

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

    /** Totales de lo que todavía no arqueó ningún cierre. */
    private Totales calcularPendiente(BigDecimal saldoInicial) {
        return new Totales(saldoInicial,
                movimientoCajaRepository.totalPendientePorTipoYMedio(TipoMovimiento.Ingreso, TipoPago.Efectivo),
                movimientoCajaRepository.totalPendientePorTipoYMedio(TipoMovimiento.Egreso, TipoPago.Efectivo),
                movimientoCajaRepository.totalPendientePorTipoYMedio(TipoMovimiento.Ingreso, TipoPago.Transferencia),
                movimientoCajaRepository.totalPendientePorTipoYMedio(TipoMovimiento.Egreso, TipoPago.Transferencia));
    }

    /** Totales de lo que quedó asignado a un cierre. */
    private Totales calcularDeCierre(BigDecimal saldoInicial, Long cierreId) {
        return new Totales(saldoInicial,
                movimientoCajaRepository.totalDeCierrePorTipoYMedio(
                        TipoMovimiento.Ingreso, TipoPago.Efectivo, cierreId),
                movimientoCajaRepository.totalDeCierrePorTipoYMedio(
                        TipoMovimiento.Egreso, TipoPago.Efectivo, cierreId),
                movimientoCajaRepository.totalDeCierrePorTipoYMedio(
                        TipoMovimiento.Ingreso, TipoPago.Transferencia, cierreId),
                movimientoCajaRepository.totalDeCierrePorTipoYMedio(
                        TipoMovimiento.Egreso, TipoPago.Transferencia, cierreId));
    }

    private record Totales(BigDecimal saldoInicial, BigDecimal ingresosEfectivo, BigDecimal egresosEfectivo,
                           BigDecimal ingresosTransferencia, BigDecimal egresosTransferencia) {
        /** Lo que debería haber en el cajón: con lo que se abrió, más lo que entró, menos lo que salió. */
        BigDecimal efectivoTeorico() {
            return saldoInicial.add(ingresosEfectivo).subtract(egresosEfectivo);
        }
    }
}
