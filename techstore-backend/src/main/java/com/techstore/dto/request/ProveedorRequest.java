package com.techstore.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProveedorRequest(
        @NotBlank @Size(max = 255) String razonSocial,
        @Size(max = 255) String nombreFantasia,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String telefono,
        @Size(max = 2000) String direccion,
        @Size(max = 100) String ciudad,
        @Size(max = 100) String pais,
        @Size(max = 20) String ruc) {
}
