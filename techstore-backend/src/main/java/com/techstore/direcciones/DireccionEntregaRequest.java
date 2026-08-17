package com.techstore.direcciones;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DireccionEntregaRequest(
        @NotBlank @Size(max = 120) String nombreDestinatario,
        @NotBlank @Size(max = 30) String telefono,
        @NotBlank @Size(max = 80) String departamento,
        @NotBlank @Size(max = 100) String ciudad,
        @Size(max = 100) String barrio,
        @NotBlank @Size(max = 200) String direccionLinea1,
        @Size(max = 200) String direccionLinea2,
        @Size(max = 40) String numeroCasa,
        @Size(max = 300) String referencia,
        @Size(max = 20) String codigoPostal,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud,
        Boolean principal) {
}

