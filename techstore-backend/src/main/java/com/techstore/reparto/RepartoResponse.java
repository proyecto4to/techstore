package com.techstore.reparto;

import com.techstore.ventas.DireccionPedidoSnapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RepartoResponse(
        Long id,
        Long pedidoId,
        Integer pedidoNumero,
        Long repartidorId,
        String repartidor,
        Long clienteId,
        String cliente,
        String clienteEmail,
        String telefono,
        String direccion,
        BigDecimal latitud,
        BigDecimal longitud,
        BigDecimal importe,
        String moneda,
        String formaPago,
        String estadoPago,
        String estadoEntrega,
        Integer ordenEntrega,
        String observaciones,
        LocalDateTime fechaAsignacion,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFinalizacion,
        String resultadoFinal,
        Boolean cobroRegistrado) {

    public static RepartoResponse from(AsignacionReparto asignacion, boolean cobrado) {
        var pedido = asignacion.getPedido();
        var cliente = pedido.getUsuario();
        DireccionPedidoSnapshot d = pedido.getDireccionEntrega();
        String direccion = d == null ? null : String.join(", ",
                java.util.stream.Stream.of(d.getDireccionLinea1(), d.getNumeroCasa(), d.getBarrio(), d.getCiudad())
                        .filter(v -> v != null && !v.isBlank()).toList());
        return new RepartoResponse(
                asignacion.getId(), pedido.getId(), pedido.getNumero(),
                asignacion.getRepartidor().getId(),
                (asignacion.getRepartidor().getNombre() + " "
                        + asignacion.getRepartidor().getApellido()).trim(),
                cliente.getId(),
                (cliente.getNombre() + " " + cliente.getApellido()).trim(), cliente.getEmail(),
                d == null ? null : d.getTelefono(), direccion,
                d == null ? null : d.getLatitud(), d == null ? null : d.getLongitud(),
                pedido.getTotal().monto(), pedido.getMoneda().codigo(),
                pedido.getMetodoPago() == null ? null : pedido.getMetodoPago().name(),
                switch (pedido.getEstado()) {
                    case G, N -> "PAGADO";
                    case X, R -> "CANCELADO";
                    default -> "PENDIENTE";
                },
                asignacion.getEstado().name(), asignacion.getOrdenEntrega(), asignacion.getObservaciones(),
                asignacion.getFechaAsignacion(), asignacion.getFechaInicio(), asignacion.getFechaFinalizacion(),
                asignacion.getResultadoFinal(), cobrado);
    }
}
