package com.techstore.envios;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActualizarEnvioRequest(
        @NotNull EstadoEnvio estado,
        @Size(max = 300) String descripcion,
        @Size(max = 200) String ubicacion,
        @Size(max = 120) String codigoSeguimiento,
        @Size(max = 120) String transportista) { }
