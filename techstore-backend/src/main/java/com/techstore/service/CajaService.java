package com.techstore.service;

import com.techstore.dto.request.RegistroMovimientoRequest;
import com.techstore.dto.response.MovimientoCajaResponse;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.ResumenCajaResponse;
import com.techstore.entity.MovimientoCaja;
import com.techstore.entity.TipoMovimiento;
import com.techstore.entity.TipoPago;
import com.techstore.entity.Usuario;
import com.techstore.repository.MovimientoCajaRepository;
import com.techstore.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CajaService {

    private static final String ENTIDAD_CAJA = "CAJA";
    private static final LocalDate ORIGEN = LocalDate.of(2000, 1, 1);

    private final MovimientoCajaRepository movimientoCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;
    private final ReportePdfService reportePdfService;

    @Transactional
    public MovimientoCajaResponse registrar(RegistroMovimientoRequest request, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        TipoPago medio = request.medio() != null ? request.medio() : TipoPago.Efectivo;
        return MovimientoCajaResponse.from(guardar(
                request.tipo(), medio, request.monto(), request.concepto(), request.referencia(), usuario));
    }

    /**
     * Ingreso generado por el sistema (p. ej. al cobrar un pedido), con el
     * medio de pago para que el cierre de caja concilie el efectivo.
     * Debe invocarse dentro de la transacción del llamador.
     */
    @Transactional
    public MovimientoCaja registrarIngresoAutomatico(
            BigDecimal monto, String concepto, String referencia, Usuario usuario, TipoPago medio) {
        return guardar(TipoMovimiento.Ingreso, medio, monto, concepto, referencia, usuario);
    }

    /**
     * Egreso generado por el sistema (p. ej. al devolver dinero por una
     * nota de crédito). Sale de la caja física, por eso se registra en efectivo.
     * Debe invocarse dentro de la transacción del llamador.
     */
    @Transactional
    public MovimientoCaja registrarEgresoAutomatico(
            BigDecimal monto, String concepto, String referencia, Usuario usuario) {
        return registrarEgresoAutomatico(monto, concepto, referencia, usuario, TipoPago.Efectivo);
    }

    /**
     * Egreso generado por el sistema con el medio de pago indicado (p. ej. el
     * pago a un proveedor por transferencia), para que el cierre de caja concilie
     * el efectivo por separado. Debe invocarse dentro de la transacción del llamador.
     */
    @Transactional
    public MovimientoCaja registrarEgresoAutomatico(
            BigDecimal monto, String concepto, String referencia, Usuario usuario, TipoPago medio) {
        return guardar(TipoMovimiento.Egreso, medio != null ? medio : TipoPago.Efectivo,
                monto, concepto, referencia, usuario);
    }

    private MovimientoCaja guardar(TipoMovimiento tipo, TipoPago medio, BigDecimal monto,
                                   String concepto, String referencia, Usuario usuario) {
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setNumero(autonumeradorService.siguiente(ENTIDAD_CAJA));
        movimiento.setTipo(tipo);
        movimiento.setMedio(medio);
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

    private BigDecimal total(TipoMovimiento tipo, TipoPago medio, LocalDateTime[] rango) {
        return movimientoCajaRepository.totalPorTipoYMedioEntre(tipo, medio, rango[0], rango[1]);
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
