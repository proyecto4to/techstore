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
import java.util.Set;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sync")
@SecurityRequirement(name = "bearerAuth")
public class SyncController {

    /**
     * Los agregados que puede ver quien no opera la tienda.
     *
     * <p>El flujo de eventos existe para que cada cliente sepa qué caché
     * invalidar, no para contarle lo que pasa en el negocio. Sin esta lista, un
     * usuario con rol Cliente también recibía {@code CASH_MOVEMENT},
     * {@code CASH_OPENING}, {@code CASH_CLOSING}, {@code PAYMENT},
     * {@code INVOICE}, {@code USER} y {@code DELIVERY_ADDRESS}: la actividad de
     * la caja, los pagos y los pedidos de otros clientes, las direcciones
     * ajenas y el alta y baja de cada usuario.
     *
     * <p>Quedan los del catálogo, que es público de todos modos, y los del
     * pedido y el envío, que son los que hacen que la persona vea avanzar su
     * propia compra. Esos dos siguen revelando que <em>algún</em> pedido se
     * movió: el evento no trae dueño, así que acotarlo más pide una columna
     * nueva en la bitácora.
     */
    static final Set<String> AGREGADOS_SIN_OPERACION =
            Set.of("PRODUCT", "BRAND", "MODEL", "ORDER", "ORDER_ITEM", "SHIPMENT");

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
                : store.after(after, limit, AGREGADOS_SIN_OPERACION);
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
