package com.techstore.service;

import com.techstore.dto.request.AbrirCajaRequest;
import com.techstore.dto.request.CerrarCajaRequest;
import com.techstore.dto.response.AperturaCajaResponse;
import com.techstore.dto.response.CierreCajaResponse;
import com.techstore.dto.response.PreviewCierreResponse;
import com.techstore.entity.AperturaCaja;
import com.techstore.entity.CierreCaja;
import com.techstore.entity.Rol;
import com.techstore.entity.TipoMovimiento;
import com.techstore.entity.TipoPago;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.repository.AperturaCajaRepository;
import com.techstore.repository.CierreCajaRepository;
import com.techstore.repository.MovimientoCajaRepository;
import com.techstore.repository.UsuarioRepository;
import com.techstore.util.Dinero;
import com.techstore.util.Moneda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CierreCajaServiceTest {

    @Mock
    private CierreCajaRepository cierreCajaRepository;

    @Mock
    private AperturaCajaRepository aperturaCajaRepository;

    @Mock
    private MovimientoCajaRepository movimientoCajaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AutonumeradorService autonumeradorService;

    @Mock
    private AuditoriaService auditoriaService;

    private CierreCajaService cierreCajaService;
    private Usuario cajero;

    @BeforeEach
    void setUp() {
        cierreCajaService = new CierreCajaService(
                cierreCajaRepository, aperturaCajaRepository, movimientoCajaRepository,
                usuarioRepository, autonumeradorService, auditoriaService);

        Rol rol = new Rol();
        rol.setNombre("Cajero");
        cajero = new Usuario();
        cajero.setId(5L);
        cajero.setEmail("cajero@techstore.py");
        cajero.setNombre("Caja");
        cajero.setApellido("Uno");
        cajero.setRol(rol);
    }

    /** Totales pendientes de arqueo (lo que ve el preview). */
    private void mockPendientes(String ingEfectivo, String egrEfectivo, String ingTransfer, String egrTransfer) {
        when(movimientoCajaRepository.totalPendientePorTipoYMedio(
                TipoMovimiento.Ingreso, TipoPago.Efectivo)).thenReturn(new BigDecimal(ingEfectivo));
        when(movimientoCajaRepository.totalPendientePorTipoYMedio(
                TipoMovimiento.Egreso, TipoPago.Efectivo)).thenReturn(new BigDecimal(egrEfectivo));
        when(movimientoCajaRepository.totalPendientePorTipoYMedio(
                TipoMovimiento.Ingreso, TipoPago.Transferencia)).thenReturn(new BigDecimal(ingTransfer));
        when(movimientoCajaRepository.totalPendientePorTipoYMedio(
                TipoMovimiento.Egreso, TipoPago.Transferencia)).thenReturn(new BigDecimal(egrTransfer));
    }

    /** Totales ya reclamados por el cierre (lo que se guarda al cerrar). */
    private void mockReclamados(String ingEfectivo, String egrEfectivo, String ingTransfer, String egrTransfer) {
        when(movimientoCajaRepository.totalDeCierrePorTipoYMedio(
                eq(TipoMovimiento.Ingreso), eq(TipoPago.Efectivo), any())).thenReturn(new BigDecimal(ingEfectivo));
        when(movimientoCajaRepository.totalDeCierrePorTipoYMedio(
                eq(TipoMovimiento.Egreso), eq(TipoPago.Efectivo), any())).thenReturn(new BigDecimal(egrEfectivo));
        when(movimientoCajaRepository.totalDeCierrePorTipoYMedio(
                eq(TipoMovimiento.Ingreso), eq(TipoPago.Transferencia), any())).thenReturn(new BigDecimal(ingTransfer));
        when(movimientoCajaRepository.totalDeCierrePorTipoYMedio(
                eq(TipoMovimiento.Egreso), eq(TipoPago.Transferencia), any())).thenReturn(new BigDecimal(egrTransfer));
    }

    /** Turno abierto con el saldo inicial indicado. */
    private AperturaCaja aperturaCon(String saldoInicial) {
        AperturaCaja apertura = new AperturaCaja();
        apertura.setId(1L);
        apertura.setNumero(1);
        apertura.setUsuario(cajero);
        apertura.setSaldoInicial(Dinero.de(new BigDecimal(saldoInicial), Moneda.PYG));
        return apertura;
    }

    @Test
    void previewSumaElSaldoInicialAlEfectivoTeorico() {
        when(cierreCajaRepository.findTopByOrderByFechaCierreDesc()).thenReturn(Optional.empty());
        when(aperturaCajaRepository.findByCierreIsNull()).thenReturn(Optional.of(aperturaCon("500000")));
        mockPendientes("5000000", "1200000", "800000", "300000");

        PreviewCierreResponse preview = cierreCajaService.preview();

        assertEquals(new BigDecimal("500000"), preview.saldoInicial());
        assertEquals(new BigDecimal("5000000"), preview.ingresosEfectivo());
        assertEquals(new BigDecimal("1200000"), preview.egresosEfectivo());
        assertEquals(new BigDecimal("800000"), preview.ingresosTransferencia());
        assertEquals(new BigDecimal("300000"), preview.egresosTransferencia());
        // saldo inicial + ingresos - egresos (las transferencias no tocan el cajón)
        assertEquals(new BigDecimal("4300000"), preview.efectivoTeorico());
    }

    @Test
    void previewSinCajaAbiertaAsumeSaldoInicialCero() {
        when(cierreCajaRepository.findTopByOrderByFechaCierreDesc()).thenReturn(Optional.empty());
        when(aperturaCajaRepository.findByCierreIsNull()).thenReturn(Optional.empty());
        mockPendientes("100000", "0", "0", "0");

        PreviewCierreResponse preview = cierreCajaService.preview();

        assertEquals(BigDecimal.ZERO, preview.saldoInicial());
        assertEquals(new BigDecimal("100000"), preview.efectivoTeorico());
    }

    @Test
    void cierreCalculaDiferenciaContraElConteoFisico() {
        when(cierreCajaRepository.findTopByOrderByFechaCierreDesc()).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(aperturaCajaRepository.findAbiertaForUpdate()).thenReturn(Optional.of(aperturaCon("500000")));
        when(autonumeradorService.siguiente("CIERRECAJA")).thenReturn(1);
        when(cierreCajaRepository.saveAndFlush(any(CierreCaja.class))).thenAnswer(inv -> inv.getArgument(0));
        // teórico 500.000 + 5.000.000 - 1.200.000 = 4.300.000
        mockReclamados("5000000", "1200000", "800000", "300000");

        // El cajero cuenta 4.250.000 -> faltan 50.000
        CierreCajaResponse cierre = cierreCajaService.cerrar(
                new CerrarCajaRequest(new BigDecimal("4250000"), "Turno mañana"), "cajero@techstore.py");

        assertEquals(new BigDecimal("500000"), cierre.saldoInicial());
        assertEquals(new BigDecimal("4300000"), cierre.efectivoTeorico());
        assertEquals(new BigDecimal("4250000"), cierre.efectivoContado());
        assertEquals(new BigDecimal("-50000"), cierre.diferencia());
        assertEquals(new BigDecimal("300000"), cierre.egresosTransferencia());
    }

    @Test
    void cerrarReclamaLosMovimientosPendientesAntesDeSumarlos() {
        when(cierreCajaRepository.findTopByOrderByFechaCierreDesc()).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(aperturaCajaRepository.findAbiertaForUpdate()).thenReturn(Optional.of(aperturaCon("0")));
        when(autonumeradorService.siguiente("CIERRECAJA")).thenReturn(1);
        when(cierreCajaRepository.saveAndFlush(any(CierreCaja.class))).thenAnswer(inv -> inv.getArgument(0));
        mockReclamados("100000", "0", "0", "0");

        cierreCajaService.cerrar(new CerrarCajaRequest(new BigDecimal("100000"), null), "cajero@techstore.py");

        // El arqueo se apoya en lo reclamado, no en un rango de fechas: así un
        // movimiento que se confirma tarde queda pendiente para el próximo cierre.
        InOrder orden = inOrder(movimientoCajaRepository);
        orden.verify(movimientoCajaRepository).asignarPendientesA(any(CierreCaja.class));
        orden.verify(movimientoCajaRepository, atLeastOnce())
                .totalDeCierrePorTipoYMedio(any(), any(), any());
        verify(movimientoCajaRepository, never()).totalPendientePorTipoYMedio(any(), any());
    }

    @Test
    void noSePuedeCerrarSinHaberAbiertoLaCaja() {
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(aperturaCajaRepository.findAbiertaForUpdate()).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> cierreCajaService.cerrar(
                new CerrarCajaRequest(new BigDecimal("100000"), null), "cajero@techstore.py"));
    }

    @Test
    void noSePuedeAbrirLaCajaDosVeces() {
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        AperturaCaja abierta = aperturaCon("500000");
        abierta.setFechaApertura(LocalDateTime.of(2026, 7, 29, 8, 0));
        when(aperturaCajaRepository.findAbiertaForUpdate()).thenReturn(Optional.of(abierta));

        assertThrows(BusinessException.class, () -> cierreCajaService.abrir(
                new AbrirCajaRequest(new BigDecimal("300000"), null), "cajero@techstore.py"));
    }

    @Test
    void abrirRegistraElSaldoInicialDelTurno() {
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(aperturaCajaRepository.findAbiertaForUpdate()).thenReturn(Optional.empty());
        when(autonumeradorService.siguiente("APERTURACAJA")).thenReturn(7);
        when(aperturaCajaRepository.save(any(AperturaCaja.class))).thenAnswer(inv -> inv.getArgument(0));

        AperturaCajaResponse apertura = cierreCajaService.abrir(
                new AbrirCajaRequest(new BigDecimal("500000"), "Fondo fijo"), "cajero@techstore.py");

        assertTrue(apertura.abierta());
        assertEquals(7, apertura.numero());
        assertEquals(new BigDecimal("500000"), apertura.saldoInicial());
    }

    @Test
    void aperturaActualInformaCuandoLaCajaEstaCerrada() {
        when(aperturaCajaRepository.findByCierreIsNull()).thenReturn(Optional.empty());

        assertFalse(cierreCajaService.aperturaActual().abierta());
    }

    @Test
    void turnoEmpiezaEnElUltimoCierre() {
        CierreCaja ultimo = new CierreCaja();
        ultimo.setFechaCierre(LocalDateTime.of(2026, 7, 20, 12, 0));
        when(cierreCajaRepository.findTopByOrderByFechaCierreDesc()).thenReturn(Optional.of(ultimo));
        lenient().when(aperturaCajaRepository.findByCierreIsNull()).thenReturn(Optional.empty());
        mockPendientes("100000", "0", "0", "0");

        PreviewCierreResponse preview = cierreCajaService.preview();

        assertEquals(LocalDateTime.of(2026, 7, 20, 12, 0), preview.desde());
    }
}
