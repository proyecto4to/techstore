package com.techstore.vehiculos;

import java.util.List;

public record HistorialVehiculoResponse(
        VehiculoResponse vehiculo,
        List<UsoVehiculoResponse> usos,
        List<EventoVehiculoResponse> eventos) {
}
