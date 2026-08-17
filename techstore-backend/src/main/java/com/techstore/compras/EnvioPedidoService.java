package com.techstore.compras;

import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import com.techstore.notificaciones.CorreoService;
import com.techstore.notificaciones.EmailTemplateService;
import com.techstore.notificaciones.EstadoCorreo;
import com.techstore.notificaciones.TipoCorreo;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoDetalle;
import com.techstore.ventas.PedidoRepository;
import com.techstore.ventas.TipoPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le manda al proveedor el pedido que se cargó en el sistema.
 *
 * <p>Antes de esto una compra se registraba acá adentro y el pedido se le
 * pasaba al proveedor por afuera —un llamado, un mensaje suelto—, así que el
 * sistema sabía qué se había pedido pero no si el proveedor se había enterado.
 *
 * <p><b>Mandarlo no lo confirma.</b> La compra sigue Pendiente hasta que llega
 * la mercadería: lo único que cambia es que queda registrado cuándo salió y por
 * dónde. Interpretar la respuesta del proveedor para saber si acepta o propone
 * cambios es un paso aparte, todavía no implementado.
 */
@Service
@RequiredArgsConstructor
public class EnvioPedidoService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PedidoRepository pedidoRepository;
    private final CorreoService correoService;
    private final EmailTemplateService emailTemplateService;
    private final EmpresaConfigService empresaConfig;

    @Transactional
    public EnvioPedidoResponse enviar(Long compraId, CanalEnvio canal) {
        Pedido compra = buscarCompra(compraId);

        if (compra.getEstado() != EstadoPedido.P) {
            // Mandar el pedido de una compra que ya llegó, o que se anuló, no
            // tiene sentido: lo que se está pidiendo ya pasó.
            throw new BusinessException("compras.envio.solo.pendiente", compra.getEstado());
        }
        exigirCanalDisponible(canal);

        Proveedor proveedor = compra.getProveedor();
        if (proveedor == null) {
            throw new BusinessException("compras.envio.sin.proveedor", compra.getNumero());
        }
        String destino = limpio(proveedor.getEmail());
        if (destino == null) {
            throw new BusinessException("compras.envio.proveedor.sin.correo", proveedor.getRazonSocial());
        }

        EstadoCorreo resultado = mandarCorreo(compra, proveedor, destino);

        // Solo se marca como enviado lo que efectivamente salió. Si quedó
        // simulado —sin SMTP configurado— o el servidor lo rechazó, decir que se
        // mandó sería mentir, y quien mira la lista de compras necesita saber a
        // cuáles todavía les falta avisarle al proveedor.
        if (resultado == EstadoCorreo.ENVIADO) {
            compra.setEnviadoEn(LocalDateTime.now());
            compra.setEnviadoCanal(canal);
            pedidoRepository.save(compra);
        }

        return EnvioPedidoResponse.de(compra, canal, destino, resultado);
    }

    /**
     * WhatsApp figura en el modelo pero no se puede mandar todavía, y el usuario
     * tiene que enterarse antes y no después: si el canal es "ambos" y se
     * mandara igual el correo, quedaría medio hecho sin que nadie lo sepa.
     */
    private static void exigirCanalDisponible(CanalEnvio canal) {
        if (canal.incluyeWhatsApp()) {
            throw new BusinessException("compras.envio.whatsapp.no.disponible");
        }
    }

    private EstadoCorreo mandarCorreo(Pedido compra, Proveedor proveedor, String destino) {
        EmpresaProperties emisor = empresaConfig.actual();
        NumberFormat formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PY"));
        formato.setMaximumFractionDigits(2);
        formato.setMinimumFractionDigits(0);

        List<List<String>> filas = new ArrayList<>();
        for (PedidoDetalle detalle : compra.getDetalles()) {
            filas.add(List.of(
                    detalle.getProducto().getNombre(),
                    String.valueOf(detalle.getCantidad()),
                    formato.format(detalle.getPrecioUnitario().monto()),
                    formato.format(detalle.getSubtotal().monto())));
        }
        Dinero total = compra.getTotal();
        EmailTemplateService.Tabla tabla = new EmailTemplateService.Tabla(
                List.of("Producto", "Cantidad", "Costo unitario", "Subtotal"),
                filas,
                List.of("TOTAL", "", "", total.moneda().simbolo() + " " + formato.format(total.monto())));

        String mensaje = """
                Te pasamos el siguiente pedido de compra.

                Emitido por: %s (RUC %s)
                Fecha: %s
                Pedido N.º: %d%s""".formatted(
                emisor.nombre(),
                emisor.ruc(),
                compra.getFecha().format(FECHA),
                compra.getNumero(),
                compra.getNotas() == null || compra.getNotas().isBlank()
                        ? "" : "\n\nObservaciones: " + compra.getNotas());

        String cuerpo = emailTemplateService.construir(
                TipoCorreo.PEDIDO_PROVEEDOR,
                "Pedido de compra N.º " + compra.getNumero(),
                proveedor.getRazonSocial(),
                mensaje,
                "Respondenos confirmando disponibilidad, precio y en cuántos días podés entregarlo.",
                null, null,
                tabla);

        return correoService.enviar(
                TipoCorreo.PEDIDO_PROVEEDOR,
                destino,
                proveedor.getRazonSocial(),
                "Pedido de compra N.º %d — %s".formatted(compra.getNumero(), emisor.nombre()),
                cuerpo,
                "COMPRA-" + compra.getNumero());
    }

    private Pedido buscarCompra(Long id) {
        Pedido pedido = pedidoRepository.findWithDetallesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("compras.no.encontrada", id));
        if (pedido.getTipo() != TipoPedido.Compra) {
            throw new BusinessException("compras.pedido.no.es.compra", pedido.getNumero());
        }
        return pedido;
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
