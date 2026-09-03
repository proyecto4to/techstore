package com.techstore.vehiculos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolverEventoVehiculoRequest(
        @NotNull EstadoVehiculo estadoVehiculo,
        @Size(max = 2000) String observacion) {
}
