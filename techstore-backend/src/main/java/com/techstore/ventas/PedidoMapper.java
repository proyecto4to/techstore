package com.techstore.ventas;

import com.techstore.comun.DineroMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PedidoMapper extends DineroMapper {

    @Mapping(target = "usuarioEmail", source = "usuario.email")
    @Mapping(target = "direccionEntrega", source = "direccionEntrega")
    @Mapping(target = "facturaDisponible", expression = "java(facturaDisponible(pedido))")
    PedidoResponse toResponse(Pedido pedido);

    @Mapping(target = "usuarioEmail", source = "usuario.email")
    @Mapping(target = "clienteRuc", source = "usuario.ruc")
    @Mapping(target = "clienteRazonSocial", source = "usuario.razonSocial")
    @Mapping(target = "facturaDisponible", expression = "java(facturaDisponible(pedido))")
    PedidoResumenResponse toResumen(Pedido pedido);

    @Mapping(target = "productoId", source = "producto.id")
    @Mapping(target = "productoNombre", source = "producto.nombre")
    PedidoResponse.DetalleResponse toDetalleResponse(PedidoDetalle detalle);

    PedidoResponse.DireccionResponse toDireccionResponse(DireccionPedidoSnapshot direccion);

    default boolean facturaDisponible(Pedido pedido) {
        return pedido.getEstado() == EstadoPedido.G || pedido.getEstado() == EstadoPedido.N;
    }
}
