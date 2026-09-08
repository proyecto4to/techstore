package com.techstore.direcciones;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Una dirección de entrega, tal como la manda la aplicación.
 *
 * <p>Lo obligatorio es el punto: sin coordenadas no hay a dónde ir. El texto de
 * la calle se deduce de ese punto y viaja como comodidad —para leerlo en la
 * factura, para calcular el envío—, no como la ubicación en sí. Por eso
 * departamento, ciudad y la primera línea son opcionales: si el servicio de
 * direcciones no contesta, la ubicación igual queda bien guardada.
 *
 * <p>Lo que sigue siendo obligatorio y escrito por la persona es a quién
 * entregar y a qué teléfono llamar: eso no está en ningún mapa.
 */
public record DireccionEntregaRequest(
        @Size(max = 60) String descripcion,
        @NotBlank @Size(max = 120) String nombreDestinatario,
        @NotBlank @Size(max = 30) String telefono,
        @Size(max = 80) String departamento,
        @Size(max = 100) String ciudad,
        @Size(max = 100) String barrio,
        @Size(max = 200) String direccionLinea1,
        @Size(max = 200) String direccionLinea2,
        @Size(max = 40) String numeroCasa,
        @Size(max = 300) String referencia,
        @Size(max = 20) String codigoPostal,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud,
        Boolean principal) {
}

