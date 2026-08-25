package com.techstore.notificaciones;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroDispositivoRequest(
        @NotNull PlataformaMovil plataforma,
        @NotBlank @Size(max = 512)
        @Pattern(regexp = "^(?:(?:Expo|Exponent)PushToken\\[[A-Za-z0-9_-]+]|[A-Za-z0-9._~:-]{20,512})$")
        String pushToken,
        @NotBlank @Size(min = 16, max = 120)
        @Pattern(regexp = "^[A-Za-z0-9._:-]+$") String deviceIdentifier) { }
