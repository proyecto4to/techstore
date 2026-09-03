package com.techstore.reparto;

import com.techstore.ventas.DireccionPedidoSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Vecino más cercano sobre distancia geodésica. Es determinista, no consume
 * APIs ni claves y deja el proveedor encapsulado detrás de {@link OptimizadorRuta}.
 */
@Service
public class OptimizadorRutaHaversine implements OptimizadorRuta {
    @Value("${reparto.salida.latitud:-25.28646}")
    private BigDecimal latitudLocal;
    @Value("${reparto.salida.longitud:-57.64700}")
    private BigDecimal longitudLocal;
    @Value("${reparto.velocidad-promedio-kmh:25}")
    private double velocidadPromedio;

    @Override
    public RutaOptimizadaResponse optimizar(List<AsignacionReparto> asignaciones,
                                            BigDecimal salidaLatitud,
                                            BigDecimal salidaLongitud) {
        List<AsignacionReparto> restantes = new ArrayList<>();
        List<Long> sinUbicacion = new ArrayList<>();
        for (AsignacionReparto asignacion : asignaciones) {
            DireccionPedidoSnapshot d = asignacion.getPedido().getDireccionEntrega();
            if (d == null || d.getLatitud() == null || d.getLongitud() == null) {
                sinUbicacion.add(asignacion.getId());
            } else {
                restantes.add(asignacion);
            }
        }

        double lat = valor(salidaLatitud, latitudLocal);
        double lon = valor(salidaLongitud, longitudLocal);
        double total = 0;
        List<RutaOptimizadaResponse.Punto> puntos = new ArrayList<>();
        while (!restantes.isEmpty()) {
            double origenLat = lat;
            double origenLon = lon;
            AsignacionReparto siguiente = restantes.stream()
                    .min(Comparator.comparingDouble(a -> distancia(origenLat, origenLon,
                            a.getPedido().getDireccionEntrega().getLatitud().doubleValue(),
                            a.getPedido().getDireccionEntrega().getLongitud().doubleValue())))
                    .orElseThrow();
            restantes.remove(siguiente);
            DireccionPedidoSnapshot d = siguiente.getPedido().getDireccionEntrega();
            double tramo = distancia(lat, lon, d.getLatitud().doubleValue(), d.getLongitud().doubleValue());
            total += tramo;
            lat = d.getLatitud().doubleValue();
            lon = d.getLongitud().doubleValue();
            puntos.add(new RutaOptimizadaResponse.Punto(puntos.size() + 1, siguiente.getId(),
                    siguiente.getPedido().getId(), siguiente.getPedido().getNumero(),
                    nombre(siguiente), d.getLatitud(), d.getLongitud(), decimal(tramo)));
        }
        int minutos = velocidadPromedio <= 0 ? 0 : (int) Math.ceil(total / velocidadPromedio * 60);
        return new RutaOptimizadaResponse(decimal(total), minutos, puntos, sinUbicacion);
    }

    private static String nombre(AsignacionReparto asignacion) {
        var u = asignacion.getPedido().getUsuario();
        return (u.getNombre() + " " + u.getApellido()).trim();
    }

    private static double valor(BigDecimal solicitado, BigDecimal porDefecto) {
        return (solicitado == null ? porDefecto : solicitado).doubleValue();
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static double distancia(double lat1, double lon1, double lat2, double lon2) {
        double radio = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return radio * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
