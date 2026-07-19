package com.techstore.dto.request;

import jakarta.validation.constraints.NotNull;

public record CambioActivoRequest(@NotNull Boolean activo) {
}
