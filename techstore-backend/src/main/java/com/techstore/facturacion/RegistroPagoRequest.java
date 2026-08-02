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
        @NotEmpty(message = "{validacion.metodo.pago.obligatorio}") @Valid List<LineaPago> lineas,
        @Valid Receptor receptor) {

    public record LineaPago(
            @NotNull TipoPago tipoPago,
            @NotNull @DecimalMin(value = "0.01", message = "{validacion.monto.positivo}") BigDecimal monto,
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
            @Email(message = "{validacion.email.cliente.formato}")
            @Size(max = 255) String email,
            /**
             * Opcionales, igual que en el documento electrónico: el manual los
             * da como ocurrencia 0-1 y solo exige la dirección para exportación
             * o nota de remisión. Si vienen, quedan congelados en la factura y
             * salen impresos; si no, el KuDE no imprime esas líneas.
             */
            @Size(max = 255) String direccion,
            @Size(max = 20) String telefono,
            Boolean guardarEnCliente) {
    }
}
