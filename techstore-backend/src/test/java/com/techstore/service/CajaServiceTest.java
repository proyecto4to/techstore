package com.techstore.service;

import com.techstore.dto.request.RegistroMovimientoRequest;
import com.techstore.dto.response.MovimientoCajaResponse;
import com.techstore.dto.response.ResumenCajaResponse;
import com.techstore.entity.MovimientoCaja;
import com.techstore.entity.Rol;
import com.techstore.entity.TipoMovimiento;
import com.techstore.entity.Usuario;
import com.techstore.repository.MovimientoCajaRepository;
import com.techstore.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CajaServiceTest {

    @Mock
    private MovimientoCajaRepository movimientoCajaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AutonumeradorService autonumeradorService;

    @Mock
    private ReportePdfService reportePdfService;

    private CajaService cajaService;
    private Usuario cajero;

    @BeforeEach
    void setUp() {
        cajaService = new CajaService(
                movimientoCajaRepository, usuarioRepository, autonumeradorService, reportePdfService);

        Rol rol = new Rol();
        rol.setNombre("Cajero");
        cajero = new Usuario();
        cajero.setId(5L);
        cajero.setEmail("cajero@techstore.py");
        cajero.setNombre("Caja");
        cajero.setApellido("Uno");
        cajero.setRol(rol);
    }

    @Test
    void registraMovimientoManualConNumeroDeSecuencia() {
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(autonumeradorService.siguiente("CAJA")).thenReturn(7);
        when(movimientoCajaRepository.save(any(MovimientoCaja.class))).thenAnswer(inv -> inv.getArgument(0));

        MovimientoCajaResponse respuesta = cajaService.registrar(
                new RegistroMovimientoRequest(
                        TipoMovimiento.Egreso, new BigDecimal("150000"), "Compra de insumos", "REC-33"),
                "cajero@techstore.py");

        assertEquals(7, respuesta.numero());
        assertEquals("Egreso", respuesta.tipo());
        assertEquals(new BigDecimal("150000"), respuesta.monto());
        assertEquals("cajero@techstore.py", respuesta.usuarioEmail());
    }

    @Test
    void ingresoAutomaticoUsaElUsuarioDelCobro() {
        when(autonumeradorService.siguiente("CAJA")).thenReturn(8);
        when(movimientoCajaRepository.save(any(MovimientoCaja.class))).thenAnswer(inv -> inv.getArgument(0));

        MovimientoCaja movimiento = cajaService.registrarIngresoAutomatico(
                new BigDecimal("4700000.00"), "Cobro pedido N.º 1", "001-001-0000001", cajero);

        assertEquals(TipoMovimiento.Ingreso, movimiento.getTipo());
        assertEquals("001-001-0000001", movimiento.getReferencia());
        assertEquals(cajero, movimiento.getUsuario());
    }

    @Test
    void resumenCalculaSaldo() {
        when(movimientoCajaRepository.totalPorTipoEntre(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0) == TipoMovimiento.Ingreso
                        ? new BigDecimal("5000000") : new BigDecimal("1200000"));

        ResumenCajaResponse resumen = cajaService.resumen(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 17));

        assertEquals(new BigDecimal("5000000"), resumen.ingresos());
        assertEquals(new BigDecimal("1200000"), resumen.egresos());
        assertEquals(new BigDecimal("3800000"), resumen.saldo());
    }
}
