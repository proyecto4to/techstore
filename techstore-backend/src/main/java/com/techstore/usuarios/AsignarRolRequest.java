package com.techstore.usuarios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El rol que pasa a tener el usuario.
 *
 * <p>Viaja por nombre y no por id porque es lo que el administrador eligió en
 * una lista y lo que después aparece en el token y en la auditoría.
 */
public record AsignarRolRequest(
        @NotBlank @Size(max = 50) String rol) {
}
