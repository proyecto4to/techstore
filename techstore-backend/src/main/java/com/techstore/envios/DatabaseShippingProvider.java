package com.techstore.envios;

import com.techstore.catalogo.Producto;
import com.techstore.comun.Dinero;
import com.techstore.direcciones.DireccionEntrega;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DatabaseShippingProvider implements ShippingProvider {
    private final TarifaEnvioRepository tarifaRepository;

    @Override public String codigo() { return "DATABASE"; }

    @Override
    public List<CotizacionEnvio> cotizar(DireccionEntrega direccion,
                                          Map<Producto, Integer> items,
                                          Dinero subtotal) {
        BigDecimal peso = items.entrySet().stream()
                .map(e -> e.getKey().getPesoKg().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime ahora = LocalDateTime.now();
        return tarifaRepository.findByActivaTrueOrderByPrioridadAscIdAsc().stream()
                .filter(t -> vigente(t, ahora))
                .filter(t -> coincide(t, direccion, peso, subtotal.monto()))
                .map(t -> construir(t, subtotal, peso))
                .toList();
    }

    private boolean vigente(TarifaEnvio t, LocalDateTime ahora) {
        return (t.getVigenciaDesde() == null || !ahora.isBefore(t.getVigenciaDesde()))
                && (t.getVigenciaHasta() == null || !ahora.isAfter(t.getVigenciaHasta()));
    }

    private boolean coincide(TarifaEnvio t, DireccionEntrega d, BigDecimal peso, BigDecimal subtotal) {
        if (t.getTipo() != TipoEnvio.RETIRO_TIENDA && d == null) return false;
        if (!igualOpcional(t.getDepartamento(), d == null ? null : d.getDepartamento())) return false;
        if (!igualOpcional(t.getCiudad(), d == null ? null : d.getCiudad())) return false;
        return mayorOIgual(peso, t.getPesoMinKg()) && menorOIgual(peso, t.getPesoMaxKg())
                && mayorOIgual(subtotal, t.getSubtotalMin()) && menorOIgual(subtotal, t.getSubtotalMax());
    }

    private CotizacionEnvio construir(TarifaEnvio t, Dinero subtotal, BigDecimal peso) {
        boolean gratis = t.getTipo() == TipoEnvio.RETIRO_TIENDA
                || (t.getUmbralEnvioGratis() != null
                && subtotal.monto().compareTo(t.getUmbralEnvioGratis()) >= 0);
        Dinero costo = gratis ? Dinero.cero(subtotal.moneda()) : t.getCosto();
        LocalDate hoy = LocalDate.now();
        return new CotizacionEnvio(t.getId(), t.getCodigo(), t.getNombre(), t.getDescripcion(),
                t.getTipo(), t.getProviderCode(), t.getTransportista(), costo, t.getIva(), gratis,
                peso, hoy.plusDays(t.getDiasEstimadosMin()), hoy.plusDays(t.getDiasEstimadosMax()));
    }

    private boolean igualOpcional(String regla, String valor) {
        return regla == null || (valor != null && regla.trim().equalsIgnoreCase(valor.trim()));
    }
    private boolean mayorOIgual(BigDecimal valor, BigDecimal limite) {
        return limite == null || valor.compareTo(limite) >= 0;
    }
    private boolean menorOIgual(BigDecimal valor, BigDecimal limite) {
        return limite == null || valor.compareTo(limite) <= 0;
    }
}
