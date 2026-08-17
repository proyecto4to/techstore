package com.techstore.envios;

import com.techstore.catalogo.Producto;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.direcciones.DireccionEntrega;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseShippingProviderTest {
    @Mock TarifaEnvioRepository repository;
    DatabaseShippingProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DatabaseShippingProvider(repository);
    }

    @Test
    void filtraPorCiudadDepartamentoYPeso() {
        TarifaEnvio asuncion = tarifa(1L, "ASU", TipoEnvio.ENTREGA_LOCAL, "Asunción", null, null, null, 25_000);
        TarifaEnvio central = tarifa(2L, "CENTRAL", TipoEnvio.OTRA_CIUDAD, null, "Central", null, null, 35_000);
        TarifaEnvio carga = tarifa(3L, "CARGA", TipoEnvio.TRANSPORTADORA, null, null,
                new BigDecimal("5.001"), new BigDecimal("20"), 75_000);
        when(repository.findByActivaTrueOrderByPrioridadAscIdAsc()).thenReturn(List.of(asuncion, central, carga));

        DireccionEntrega direccion = new DireccionEntrega();
        direccion.setCiudad("Asunción");
        direccion.setDepartamento("Capital");
        List<CotizacionEnvio> resultado = provider.cotizar(direccion, items("2.5", 2), Dinero.de(500_000, Moneda.PYG));

        assertEquals(List.of("ASU"), resultado.stream().map(CotizacionEnvio::codigo).toList());
        assertEquals(new BigDecimal("5.0"), resultado.getFirst().pesoTotalKg());
    }

    @Test
    void aplicaUmbralDeEnvioGratisSinModificarLaTarifa() {
        TarifaEnvio tarifa = tarifa(1L, "LOCAL", TipoEnvio.ENTREGA_LOCAL, "Asunción", null, null, null, 25_000);
        tarifa.setUmbralEnvioGratis(new BigDecimal("1000000"));
        when(repository.findByActivaTrueOrderByPrioridadAscIdAsc()).thenReturn(List.of(tarifa));
        DireccionEntrega direccion = new DireccionEntrega();
        direccion.setCiudad("asunción");
        direccion.setDepartamento("Capital");

        CotizacionEnvio cotizacion = provider.cotizar(
                direccion, items("0.3", 1), Dinero.de(1_000_000, Moneda.PYG)).getFirst();

        assertTrue(cotizacion.gratis());
        assertTrue(cotizacion.costo().esCero());
        assertEquals(Dinero.de(25_000, Moneda.PYG), tarifa.getCosto());
    }

    @Test
    void retiroSiempreEsGratuitoYSinDireccion() {
        TarifaEnvio retiro = tarifa(1L, "RETIRO", TipoEnvio.RETIRO_TIENDA, null, null, null, null, 10_000);
        when(repository.findByActivaTrueOrderByPrioridadAscIdAsc()).thenReturn(List.of(retiro));

        CotizacionEnvio cotizacion = provider.cotizar(
                null, items("1", 1), Dinero.de(20_000, Moneda.PYG)).getFirst();

        assertTrue(cotizacion.gratis());
        assertTrue(cotizacion.costo().esCero());
    }

    private TarifaEnvio tarifa(Long id, String codigo, TipoEnvio tipo, String ciudad, String departamento,
                                BigDecimal pesoMin, BigDecimal pesoMax, long costo) {
        TarifaEnvio tarifa = new TarifaEnvio();
        tarifa.setId(id);
        tarifa.setCodigo(codigo);
        tarifa.setNombre(codigo);
        tarifa.setTipo(tipo);
        tarifa.setProviderCode("DATABASE");
        tarifa.setCiudad(ciudad);
        tarifa.setDepartamento(departamento);
        tarifa.setPesoMinKg(pesoMin);
        tarifa.setPesoMaxKg(pesoMax);
        tarifa.setCosto(Dinero.de(costo, Moneda.PYG));
        tarifa.setIva(BigDecimal.TEN);
        tarifa.setDiasEstimadosMin(1);
        tarifa.setDiasEstimadosMax(3);
        return tarifa;
    }

    private Map<Producto, Integer> items(String pesoKg, int cantidad) {
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setPesoKg(new BigDecimal(pesoKg));
        Map<Producto, Integer> items = new LinkedHashMap<>();
        items.put(producto, cantidad);
        return items;
    }
}
