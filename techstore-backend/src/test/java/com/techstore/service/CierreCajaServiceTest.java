package com.techstore.service;

import com.techstore.dto.request.CerrarCajaRequest;
import com.techstore.dto.response.CierreCajaResponse;
import com.techstore.dto.response.PreviewCierreResponse;
import com.techstore.entity.CierreCaja;
import com.techstore.entity.Rol;
import com.techstore.entity.TipoMovimiento;
import com.techstore.entity.TipoPago;
import com.techstore.entity.Usuario;
import com.techstore.repository.CierreCajaRepository;
import com.techstore.repository.MovimientoCajaRepository;
import com.techstore.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CierreCajaServiceTest {

    @Mock
    private CierreCajaRepository cierreCajaRepository;

    @Mock
    private MovimientoCajaRepository movimientoCajaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AutonumeradorService autonumeradorService;

    private CierreCajaService cierreCajaService;
    private Usuario cajero;

    @BeforeEach
    void setUp() {
        cierreCajaService = new CierreCajaService(
                cierreCajaRepository, movimientoCajaRepository, usuarioRepository, autonumeradorService);

        Rol rol = new Rol();
        rol.setNombre("Cajero");
        cajero = new Usuario();
        cajero.setId(5L);
        cajero.setEmail("cajero@techstore.py");
        cajero.setNombre("Caja");
        cajero.setApellido("Uno");
        cajero.setRol(rol);
    }

    private void mockTotales(String ingEfectivo, String egrEfectivo, String ingTransfer) {
        when(movimientoCajaRepository.totalPorTipoYMedioEntre(
                eq(TipoMovimiento.Ingreso), eq(TipoPago.Efectivo), any(), any()))
                .thenReturn(new BigDecimal(ingEfectivo));
        when(movimientoCajaRepository.totalPorTipoYMedioEntre(
                eq(TipoMovimiento.Egreso), eq(TipoPago.Efectivo), any(), any()))
                .thenReturn(new BigDecimal(egrEfectivo));
        when(movimientoCajaRepository.totalPorTipoYMedioEntre(
                eq(TipoMovimiento.Ingreso), eq(TipoPago.Transferencia), any(), any()))
                .thenReturn(new BigDecimal(ingTransfer));
    }

    @Test
    void previewCalculaEfectivoTeoricoDelTurno() {
        when(cierreCajaRepository.findTopByOrderByFechaCierreDesc()).thenReturn(Optional.empty());
        mockTotales("5000000", "1200000", "800000");

        PreviewCierreResponse preview = cierreCajaService.preview();

        assertEquals(new BigDecimal("5000000"), preview.ingresosEfectivo());
        assertEquals(new BigDecimal("1200000"), preview.egresosEfectivo());
        assertEquals(new BigDecimal("800000"), preview.ingresosTransferencia());
        // Efectivo teórico = ingresos efectivo - egresos efectivo (la transferencia no entra)
        assertEquals(new BigDecimal("3800000"), preview.efectivoTeorico());
    }

    @Test
    void cierreCalculaDiferenciaContraElConteoFisico() {
        when(cierreCajaRepository.findTopByOrderByFechaCierreDesc()).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(autonumeradorService.siguiente("CIERRECAJA")).thenReturn(1);
        when(cierreCajaRepository.save(any(CierreCaja.class))).thenAnswer(inv -> inv.getArgument(0));
        mockTotales("5000000", "1200000", "800000"); // teórico 3.800.000

        // El cajero cuenta 3.750.000 -> faltan 50.000
        CierreCajaResponse cierre = cierreCajaService.cerrar(
                new CerrarCajaRequest(new BigDecimal("3750000"), "Turno mañana"), "cajero@techstore.py");

        assertEquals(new BigDecimal("3800000"), cierre.efectivoTeorico());
        assertEquals(new BigDecimal("3750000"), cierre.efectivoContado());
        assertEquals(new BigDecimal("-50000"), cierre.diferencia());
    }

    @Test
    void turnoEmpiezaEnElUltimoCierre() {
        CierreCaja ultimo = new CierreCaja();
        ultimo.setFechaCierre(LocalDateTime.of(2026, 7, 20, 12, 0));
        when(cierreCajaRepository.findTopByOrderByFechaCierreDesc()).thenReturn(Optional.of(ultimo));
        mockTotales("100000", "0", "0");

        PreviewCierreResponse preview = cierreCajaService.preview();

        assertEquals(LocalDateTime.of(2026, 7, 20, 12, 0), preview.desde());
    }
}
