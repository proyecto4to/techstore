package com.techstore.service;

import com.techstore.dto.request.RegistroMovimientoRequest;
import com.techstore.dto.response.MovimientoCajaResponse;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.ResumenCajaResponse;
import com.techstore.entity.MovimientoCaja;
import com.techstore.entity.TipoMovimiento;
import com.techstore.entity.Usuario;
import com.techstore.repository.MovimientoCajaRepository;
import com.techstore.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CajaService {

    private static final String ENTIDAD_CAJA = "CAJA";
    private static final LocalDate ORIGEN = LocalDate.of(2000, 1, 1);

    private final MovimientoCajaRepository movimientoCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;

    @Transactional
    public MovimientoCajaResponse registrar(RegistroMovimientoRequest request, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        return MovimientoCajaResponse.from(guardar(
                request.tipo(), request.monto(), request.concepto(), request.referencia(), usuario));
    }

    /**
     * Ingreso generado por el sistema (p. ej. al cobrar un pedido).
     * Debe invocarse dentro de la transacción del llamador.
     */
    @Transactional
    public MovimientoCaja registrarIngresoAutomatico(
            BigDecimal monto, String concepto, String referencia, Usuario usuario) {
        return guardar(TipoMovimiento.Ingreso, monto, concepto, referencia, usuario);
    }

    /**
     * Egreso generado por el sistema (p. ej. al devolver dinero por una
     * nota de crédito). Debe invocarse dentro de la transacción del llamador.
     */
    @Transactional
    public MovimientoCaja registrarEgresoAutomatico(
            BigDecimal monto, String concepto, String referencia, Usuario usuario) {
        return guardar(TipoMovimiento.Egreso, monto, concepto, referencia, usuario);
    }

    private MovimientoCaja guardar(
            TipoMovimiento tipo, BigDecimal monto, String concepto, String referencia, Usuario usuario) {
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setNumero(autonumeradorService.siguiente(ENTIDAD_CAJA));
        movimiento.setTipo(tipo);
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

        BigDecimal ingresos = movimientoCajaRepository
                .totalPorTipoEntre(TipoMovimiento.Ingreso, rango[0], rango[1]);
        BigDecimal egresos = movimientoCajaRepository
                .totalPorTipoEntre(TipoMovimiento.Egreso, rango[0], rango[1]);

        return new ResumenCajaResponse(
                desdeEfectivo, hastaEfectivo, ingresos, egresos, ingresos.subtract(egresos));
    }

    private static LocalDateTime[] rango(LocalDate desde, LocalDate hasta) {
        LocalDateTime inicio = (desde != null ? desde : ORIGEN).atStartOfDay();
        LocalDateTime fin = (hasta != null ? hasta : LocalDate.now()).plusDays(1).atStartOfDay();
        return new LocalDateTime[]{inicio, fin};
    }
}
