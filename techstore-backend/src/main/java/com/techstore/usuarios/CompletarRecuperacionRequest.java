package com.techstore.usuarios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompletarRecuperacionRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 100, message = "{validacion.password.longitud}")
        @Pattern(regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+",
                message = "{validacion.password.complejidad}")
        String password) {
}
