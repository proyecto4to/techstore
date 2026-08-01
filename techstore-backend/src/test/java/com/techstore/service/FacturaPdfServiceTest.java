package com.techstore.service;

import com.techstore.config.EmpresaProperties;
import com.techstore.entity.EstadoFactura;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.Factura;
import com.techstore.entity.Pedido;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Producto;
import com.techstore.entity.Rol;
import com.techstore.entity.TipoPedido;
import com.techstore.entity.Usuario;
import com.techstore.util.Dinero;
import com.techstore.util.Moneda;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FacturaPdfServiceTest {

    private final FacturaPdfService servicio = new FacturaPdfService(configConEmisor(new EmpresaProperties(
            "TechStore Paraguay", "80012345-6", "Avda. España 1234 c/ Brasil", "Asunción, Paraguay",
            "(021) 555-123", "Venta de equipos e insumos informáticos", "12345678",
            "01/01/2026 al 31/12/2026",
            2, "001", "001", "47411", "11", "CENTRAL", "1", "ASUNCION", "1", "ASUNCION", "2026-01-01")));

    /**
     * Config de emisor fija, sin base de datos de por medio. El stub es lenient
     * porque hay tests que fallan antes de llegar a necesitar estos datos.
     */
    static EmpresaConfigService configConEmisor(EmpresaProperties emisor) {
        EmpresaConfigService config = mock(EmpresaConfigService.class);
        lenient().when(config.actual()).thenReturn(emisor);
        return config;
    }

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
        producto.setPrecio(Dinero.de(3_500_000, Moneda.PYG));
        producto.setIva(BigDecimal.TEN);

        Pedido pedido = new Pedido();
        pedido.setNumero(7);
        pedido.setTipo(TipoPedido.Venta);
        pedido.setEstado(EstadoPedido.G);
        pedido.setUsuario(cliente);
        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setProducto(producto);
        detalle.setCantidad(1);
        detalle.setIvaAplicado(BigDecimal.TEN);
        pedido.agregarDetalle(detalle);
        detalle.setPrecioUnitario(producto.getPrecio());
        detalle.setSubtotal(producto.getPrecio());
        pedido.setTotal(producto.getPrecio());

        Factura factura = new Factura();
        factura.setNumero("001-001-0000001");
        factura.setTipo("CONTADO");
        factura.setPedido(pedido);
        factura.setFecha(LocalDateTime.of(2026, 7, 17, 10, 30));
        // 3.500.000 × 10 ÷ 110 = 318.181,81… -> 318.182 en guaraníes enteros.
        factura.setSubtotal(Dinero.de(3_181_818, Moneda.PYG));
        factura.setIva(Dinero.de(318_182, Moneda.PYG));
        factura.setTotal(Dinero.de(3_500_000, Moneda.PYG));
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
