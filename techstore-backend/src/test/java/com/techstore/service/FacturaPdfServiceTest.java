package com.techstore.service;

import com.techstore.entity.EstadoFactura;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.Factura;
import com.techstore.entity.Pedido;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Producto;
import com.techstore.entity.Rol;
import com.techstore.entity.TipoPedido;
import com.techstore.entity.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FacturaPdfServiceTest {

    private final FacturaPdfService servicio = new FacturaPdfService("TechStore Paraguay", "12345678");

    private Factura facturaDePrueba() {
        Rol rol = new Rol();
        rol.setNombre("Cliente");

        Usuario cliente = new Usuario();
        cliente.setNombre("María");
        cliente.setApellido("González");
        cliente.setEmail("maria@cliente.py");
        cliente.setRol(rol);

        Producto producto = new Producto();
        producto.setNombre("NVIDIA RTX 4090");
        producto.setPrecio(new BigDecimal("3500000.00"));
        producto.setIva(BigDecimal.TEN);

        Pedido pedido = new Pedido();
        pedido.setNumero(7);
        pedido.setTipo(TipoPedido.Venta);
        pedido.setEstado(EstadoPedido.G);
        pedido.setUsuario(cliente);
        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setProducto(producto);
        detalle.setCantidad(1);
        detalle.setPrecioUnitario(producto.getPrecio());
        detalle.setSubtotal(producto.getPrecio());
        detalle.setIvaAplicado(BigDecimal.TEN);
        pedido.agregarDetalle(detalle);
        pedido.setTotal(producto.getPrecio());

        Factura factura = new Factura();
        factura.setNumero("001-001-0000001");
        factura.setTipo("CONTADO");
        factura.setPedido(pedido);
        factura.setFecha(LocalDateTime.of(2026, 7, 17, 10, 30));
        factura.setSubtotal(new BigDecimal("3181818.18"));
        factura.setIva(new BigDecimal("318181.82"));
        factura.setTotal(new BigDecimal("3500000.00"));
        factura.setEstado(EstadoFactura.Pagada);
        factura.setUsuario(cliente);
        return factura;
    }

    @Test
    void generaUnPdfValidoYNoTrivial() {
        byte[] pdf = servicio.generar(facturaDePrueba());

        String magia = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
        assertTrue(magia.startsWith("%PDF-"), "El contenido debe ser un PDF");
        assertTrue(pdf.length > 1500, "El PDF debe tener contenido real (tiene " + pdf.length + " bytes)");
    }
}
