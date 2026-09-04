package com.techstore.caja;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.comun.AutonumeradorService;
import com.techstore.reportes.ReportePdfService;
import com.techstore.usuarios.Rol;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
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

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private AperturaCajaRepository aperturaCajaRepository;

    private CajaService cajaService;
    private Usuario cajero;

    @BeforeEach
    void setUp() {
        cajaService = new CajaService(
                movimientoCajaRepository, aperturaCajaRepository, usuarioRepository,
                autonumeradorService, reportePdfService, auditoriaService);

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
                        TipoMovimiento.Egreso, new BigDecimal("150000"), "Compra de insumos", "REC-33", null),
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
                com.techstore.comun.Dinero.de(4_700_000, com.techstore.comun.Moneda.PYG),
                "Cobro pedido N.º 1", "001-001-0000001", cajero,
                com.techstore.facturacion.TipoPago.Transferencia);

        assertEquals(TipoMovimiento.Ingreso, movimiento.getTipo());
        assertEquals(com.techstore.facturacion.TipoPago.Transferencia, movimiento.getMedio());
        assertEquals("001-001-0000001", movimiento.getReferencia());
        assertEquals(cajero, movimiento.getUsuario());
    }

    @Test
    void resumenSeparaElEfectivoDelCajonDeLoQueVaAlBanco() {
        // efectivo: entran 5.000.000, salen 1.200.000 | transferencia: entran 800.000, salen 300.000
        when(movimientoCajaRepository.totalPorTipoYMedioEntre(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    boolean ingreso = inv.getArgument(0) == TipoMovimiento.Ingreso;
                    boolean efectivo = inv.getArgument(1) == com.techstore.facturacion.TipoPago.Efectivo;
                    if (efectivo) {
                        return ingreso ? new BigDecimal("5000000") : new BigDecimal("1200000");
                    }
                    return ingreso ? new BigDecimal("800000") : new BigDecimal("300000");
                });

        ResumenCajaResponse resumen = cajaService.resumen(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 17));

        // Lo que hay en el cajón no incluye las transferencias.
        assertEquals(new BigDecimal("5000000"), resumen.ingresosEfectivo());
        assertEquals(new BigDecimal("1200000"), resumen.egresosEfectivo());
        assertEquals(new BigDecimal("3800000"), resumen.netoEfectivo());

        assertEquals(new BigDecimal("800000"), resumen.ingresosTransferencia());
        assertEquals(new BigDecimal("300000"), resumen.egresosTransferencia());
        assertEquals(new BigDecimal("500000"), resumen.netoTransferencia());

        // Los totales siguen sumando ambos medios.
        assertEquals(new BigDecimal("5800000"), resumen.ingresos());
        assertEquals(new BigDecimal("1500000"), resumen.egresos());
        assertEquals(new BigDecimal("4300000"), resumen.saldo());
    }
}
