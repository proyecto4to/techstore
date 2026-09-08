package com.techstore.sincronizacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sync")
@SecurityRequirement(name = "bearerAuth")
public class SyncController {

    /**
     * Recuperación por cursor, para lo que se perdió mientras no había
     * conexión.
     *
     * <p>Qué puede ver cada quien vive en {@link DestinosDeEventos}, compartido
     * con el canal de WebSocket. Estaban separados y solo este camino filtraba,
     * así que alcanzaba con quedarse escuchando el socket para recibir igual la
     * actividad de la caja, los pagos y las altas de usuarios.
     */
    private final DomainEventStore store;

    @GetMapping("/events")
    @Operation(summary = "Eventos de dominio desde un cursor",
            description = "Para que cada cliente sepa qué cachés invalidar. Quien no opera la tienda "
                    + "recibe solo los eventos de catálogo, pedido y envío")
    public List<DomainEventResponse> events(
            @RequestParam(defaultValue = "0") @Min(0) long after,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit,
            Authentication auth) {
        return operaLaTienda(auth)
                ? store.after(after, limit)
                : store.after(after, limit, DestinosDeEventos.PARA_QUIEN_COMPRA);
    }

    /** Admin y Cajero mueven la caja y las compras, así que ven todo el flujo. */
    private boolean operaLaTienda(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_Admin".equals(a.getAuthority())
                        || "ROLE_Cajero".equals(a.getAuthority()));
    }
}
