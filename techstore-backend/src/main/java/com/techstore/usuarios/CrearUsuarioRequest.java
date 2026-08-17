package com.techstore.usuarios;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearUsuarioRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100, message = "{validacion.password.longitud}")
        @Pattern(regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+",
                message = "{validacion.password.complejidad}")
        String password,
        @NotBlank String rol) {
}
