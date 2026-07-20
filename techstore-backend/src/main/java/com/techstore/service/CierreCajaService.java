package com.techstore.service;

import com.techstore.dto.request.CerrarCajaRequest;
import com.techstore.dto.response.CierreCajaResponse;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.PreviewCierreResponse;
import com.techstore.entity.CierreCaja;
import com.techstore.entity.TipoMovimiento;
import com.techstore.entity.TipoPago;
import com.techstore.entity.Usuario;
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
 * Cierre de caja por turno: arquea el efectivo físico contra lo que el
 * sistema calcula desde el último cierre. Las transferencias se informan
 * aparte (van al banco, no a la caja física).
 */
@Service
@RequiredArgsConstructor
public class CierreCajaService {

    private static final String ENTIDAD_CIERRE = "CIERRECAJA";
    private static final LocalDateTime ORIGEN = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final CierreCajaRepository cierreCajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;

    @Transactional(readOnly = true)
    public PreviewCierreResponse preview() {
        LocalDateTime desde = inicioTurno();
        LocalDateTime hasta = LocalDateTime.now();
        Totales t = calcular(desde, hasta);
        return new PreviewCierreResponse(desde, t.ingresosEfectivo, t.egresosEfectivo,
                t.ingresosTransferencia, t.efectivoTeorico());
    }

    @Transactional
    public CierreCajaResponse cerrar(CerrarCajaRequest request, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        LocalDateTime desde = inicioTurno();
        LocalDateTime hasta = LocalDateTime.now();
        Totales t = calcular(desde, hasta);

        CierreCaja cierre = new CierreCaja();
        cierre.setNumero(autonumeradorService.siguiente(ENTIDAD_CIERRE));
        cierre.setFechaDesde(desde);
        cierre.setFechaCierre(hasta);
        cierre.setUsuario(usuario);
        cierre.setIngresosEfectivo(t.ingresosEfectivo);
        cierre.setEgresosEfectivo(t.egresosEfectivo);
        cierre.setIngresosTransferencia(t.ingresosTransferencia);
        cierre.setEfectivoTeorico(t.efectivoTeorico());
        cierre.setEfectivoContado(request.efectivoContado());
        cierre.setDiferencia(request.efectivoContado().subtract(t.efectivoTeorico()));
        cierre.setNotas(request.notas());

        return CierreCajaResponse.from(cierreCajaRepository.save(cierre));
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

    private Totales calcular(LocalDateTime desde, LocalDateTime hasta) {
        BigDecimal ingresosEfectivo = movimientoCajaRepository
                .totalPorTipoYMedioEntre(TipoMovimiento.Ingreso, TipoPago.Efectivo, desde, hasta);
        BigDecimal egresosEfectivo = movimientoCajaRepository
                .totalPorTipoYMedioEntre(TipoMovimiento.Egreso, TipoPago.Efectivo, desde, hasta);
        BigDecimal ingresosTransferencia = movimientoCajaRepository
                .totalPorTipoYMedioEntre(TipoMovimiento.Ingreso, TipoPago.Transferencia, desde, hasta);
        return new Totales(ingresosEfectivo, egresosEfectivo, ingresosTransferencia);
    }

    private record Totales(BigDecimal ingresosEfectivo, BigDecimal egresosEfectivo,
                           BigDecimal ingresosTransferencia) {
        BigDecimal efectivoTeorico() {
            return ingresosEfectivo.subtract(egresosEfectivo);
        }
    }
}
