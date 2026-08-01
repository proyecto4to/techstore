package com.techstore.usuarios;

import jakarta.validation.constraints.NotNull;

public record CambioActivoRequest(@NotNull Boolean activo) {
}
