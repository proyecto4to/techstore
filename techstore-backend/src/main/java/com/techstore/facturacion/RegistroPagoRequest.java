package com.techstore.facturacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cobro de un pedido. Puede tener una o dos líneas (efectivo y/o
 * transferencia); la suma de los montos debe igualar el total del pedido.
 * Opcionalmente se factura a un RUC en vez de a consumidor final.
 */
public record RegistroPagoRequest(
        @NotEmpty(message = "Debe indicar al menos un método de pago") @Valid List<LineaPago> lineas,
        @Valid Receptor receptor) {

    public record LineaPago(
            @NotNull TipoPago tipoPago,
            @NotNull @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero") BigDecimal monto,
            @Size(max = 50) String numeroComprobante) {
    }

    /**
     * A quién se le factura. Si viene null, o con el RUC vacío, la factura sale
     * a consumidor final como siempre. El {@code email} es independiente del
     * RUC: sirve para mandarle la factura a un cliente de mostrador aunque no
     * facture con RUC.
     *
     * @param guardarEnCliente deja el RUC cargado en la cuenta del cliente para
     *                         no tener que dictarlo en la próxima compra
     */
    public record Receptor(
            @Size(max = 20) String ruc,
            @Size(max = 255) String razonSocial,
            @Email(message = "El correo del cliente no tiene un formato válido")
            @Size(max = 255) String email,
            Boolean guardarEnCliente) {
    }
}
