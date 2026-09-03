package com.techstore.vehiculos;

import jakarta.validation.constraints.NotNull;

public record CambioActivoVehiculoRequest(@NotNull Boolean activo) {
}
