package com.techstore.usuarios;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SolicitarRecuperacionRequest(@NotBlank @Email String email) {
}
