package com.techstore.caja;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.comun.AutonumeradorService;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.PageResponse;
import com.techstore.facturacion.TipoPago;
import com.techstore.reportes.ReportePdfService;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CajaService {

    private static final String ENTIDAD_CAJA = "CAJA";
    private static final LocalDate ORIGEN = LocalDate.of(2000, 1, 1);

    private final MovimientoCajaRepository movimientoCajaRepository;
    private final AperturaCajaRepository aperturaCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;
    private final ReportePdfService reportePdfService;
    private final AuditoriaService auditoriaService;

    @Transactional
    public MovimientoCajaResponse registrar(RegistroMovimientoRequest request, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        TipoPago medio = request.medio() != null ? request.medio() : TipoPago.Efectivo;
        // Un movimiento cargado a mano no declara moneda: se registra en la del sistema.
        MovimientoCaja movimiento = guardar(
                request.tipo(), medio, Dinero.de(request.monto(), Moneda.porDefecto()),
                request.concepto(), request.referencia(), usuario);

        // Solo los movimientos cargados a mano: los automáticos ya quedan
        // explicados por la factura o la compra que los originó.
        auditoriaService.registrar("MovimientoCaja", movimiento.getId(), AuditoriaService.Accion.Crear,
                "%s manual de %s en %s — %s".formatted(
                        movimiento.getTipo(), movimiento.getMonto(), medio, movimiento.getConcepto()),
                AuditoriaService.Cambios.nuevo()
                        .valor("numero", movimiento.getNumero())
                        .valor("monto", movimiento.getMonto())
                        .valor("medio", medio));

        return MovimientoCajaResponse.from(movimiento);
    }

    /**
     * Ingreso generado por el sistema (p. ej. al cobrar un pedido), con el
     * medio de pago para que el cierre de caja concilie el efectivo.
     * Debe invocarse dentro de la transacción del llamador.
     */
    @Transactional
    public MovimientoCaja registrarIngresoAutomatico(
            Dinero monto, String concepto, String referencia, Usuario usuario, TipoPago medio) {
        return guardar(TipoMovimiento.Ingreso, medio, monto, concepto, referencia, usuario);
    }

    /**
     * Egreso generado por el sistema (p. ej. al devolver dinero por una
     * nota de crédito). Sale de la caja física, por eso se registra en efectivo.
     * Debe invocarse dentro de la transacción del llamador.
     */
    @Transactional
    public MovimientoCaja registrarEgresoAutomatico(
            Dinero monto, String concepto, String referencia, Usuario usuario) {
        return registrarEgresoAutomatico(monto, concepto, referencia, usuario, TipoPago.Efectivo);
    }

    /**
     * Egreso generado por el sistema con el medio de pago indicado (p. ej. el
     * pago a un proveedor por transferencia), para que el cierre de caja concilie
     * el efectivo por separado. Debe invocarse dentro de la transacción del llamador.
     */
    @Transactional
    public MovimientoCaja registrarEgresoAutomatico(
            Dinero monto, String concepto, String referencia, Usuario usuario, TipoPago medio) {
        return guardar(TipoMovimiento.Egreso, medio != null ? medio : TipoPago.Efectivo,
                monto, concepto, referencia, usuario);
    }

    private MovimientoCaja guardar(TipoMovimiento tipo, TipoPago medio, Dinero monto,
                                   String concepto, String referencia, Usuario usuario) {
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setNumero(autonumeradorService.siguiente(ENTIDAD_CAJA));
        movimiento.setTipo(tipo);
        movimiento.setMedio(medio);
        movimiento.setMoneda(monto.moneda());
        movimiento.setMonto(monto);
        movimiento.setConcepto(concepto);
        movimiento.setReferencia(referencia);
        movimiento.setUsuario(usuario);
        return movimientoCajaRepository.save(movimiento);
    }

    @Transactional(readOnly = true)
    public PageResponse<MovimientoCajaResponse> listar(LocalDate desde, LocalDate hasta, Pageable pageable) {
        LocalDateTime[] rango = rango(desde, hasta);
        return PageResponse.from(movimientoCajaRepository
                .findByFechaGreaterThanEqualAndFechaLessThan(rango[0], rango[1], pageable)
                .map(MovimientoCajaResponse::from));
    }

    /**
     * Los movimientos del turno abierto, para quien opera la caja.
     *
     * <p>El cajero necesita ver lo que registró para confirmar su trabajo, pero
     * no el historial del negocio: sin este recorte podía pedir cualquier rango
     * de fechas y leer meses de movimientos, que no hacen a su turno.
     *
     * <p>Con la caja cerrada devuelve vacío en lugar de caer al historial
     * completo: si no hay turno abierto, no hay nada suyo que mostrar.
     */
    @Transactional(readOnly = true)
    public PageResponse<MovimientoCajaResponse> listarDelTurno(Pageable pageable) {
        return aperturaCajaRepository.findByCierreIsNull()
                .map(apertura -> PageResponse.from(movimientoCajaRepository
                        .findByFechaGreaterThanEqualAndFechaLessThan(
                                apertura.getFechaApertura(), LocalDateTime.now(), pageable)
                        .map(MovimientoCajaResponse::from)))
                .orElseGet(() -> new PageResponse<>(
                        List.of(), 0, pageable.getPageSize(), 0, 0));
    }

    @Transactional(readOnly = true)
    public ResumenCajaResponse resumen(LocalDate desde, LocalDate hasta) {
        LocalDate desdeEfectivo = desde != null ? desde : ORIGEN;
        LocalDate hastaEfectivo = hasta != null ? hasta : LocalDate.now();
        LocalDateTime[] rango = rango(desde, hasta);

        return ResumenCajaResponse.de(
                desdeEfectivo, hastaEfectivo,
                total(TipoMovimiento.Ingreso, TipoPago.Efectivo, rango),
                total(TipoMovimiento.Egreso, TipoPago.Efectivo, rango),
                total(TipoMovimiento.Ingreso, TipoPago.Transferencia, rango),
                total(TipoMovimiento.Egreso, TipoPago.Transferencia, rango));
    }

    /**
     * Suma los movimientos del período. La consulta agrega sin distinguir moneda,
     * lo cual es correcto mientras haya una sola: el día que se opere en otra hay
     * que agrupar por {@code moneda} y devolver un total por cada una, porque un
     * arqueo no puede sumar guaraníes con dólares.
     */
    private Dinero total(TipoMovimiento tipo, TipoPago medio, LocalDateTime[] rango) {
        return Dinero.deONulo(
                movimientoCajaRepository.totalPorTipoYMedioEntre(tipo, medio, rango[0], rango[1]),
                Moneda.porDefecto());
    }

    @Transactional(readOnly = true)
    public byte[] resumenPdf(LocalDate desde, LocalDate hasta) {
        LocalDateTime[] rango = rango(desde, hasta);
        List<MovimientoCajaResponse> movimientos = movimientoCajaRepository
                .findByFechaGreaterThanEqualAndFechaLessThan(
                        rango[0], rango[1], PageRequest.of(0, 1000, Sort.by("fecha").descending()))
                .map(MovimientoCajaResponse::from)
                .getContent();
        return reportePdfService.caja(resumen(desde, hasta), movimientos);
    }

    private static LocalDateTime[] rango(LocalDate desde, LocalDate hasta) {
        LocalDateTime inicio = (desde != null ? desde : ORIGEN).atStartOfDay();
        LocalDateTime fin = (hasta != null ? hasta : LocalDate.now()).plusDays(1).atStartOfDay();
        return new LocalDateTime[]{inicio, fin};
    }
}
