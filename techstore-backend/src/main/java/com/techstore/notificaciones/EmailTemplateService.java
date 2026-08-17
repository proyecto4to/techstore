package com.techstore.notificaciones;

import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Year;
import java.util.List;
import java.util.Objects;

/**
 * Arma el HTML de los correos de TechStore con una plantilla única (header con
 * marca, título coloreado según el {@link TipoCorreo}, mensaje, dato destacado
 * opcional y botón de acción opcional). Mismo estilo visual que la app: fondo
 * oscuro y colores de Tailwind (ver tailwind.config.js).
 */
@Service
public class EmailTemplateService {

    private final EmpresaConfigService empresaConfig;
    private final String frontendUrl;

    /**
     * Toma los datos del emisor de la configuración vigente y no de
     * {@code application.properties}: si alguien cambió el nombre o el correo de
     * la empresa desde Admin → Empresa, el correo que sale tiene que decir eso.
     * Importa sobre todo en el pedido a proveedor, donde el pie promete a qué
     * casilla llega la respuesta.
     */
    public EmailTemplateService(EmpresaConfigService empresaConfig,
                                @Value("${app.frontend-url}") String frontendUrl) {
        this.empresaConfig = empresaConfig;
        this.frontendUrl = frontendUrl;
    }

    private EmpresaProperties empresa() {
        return empresaConfig.actual();
    }

    /** URL absoluta de la tienda a partir de una ruta relativa (ej. "/mi-cuenta"). */
    public String url(String rutaRelativa) {
        return frontendUrl.replaceAll("/+$", "") + rutaRelativa;
    }

    /**
     * Una tabla de detalle dentro del correo: el listado de ítems de un pedido,
     * por ejemplo.
     *
     * <p>Existe porque el mensaje principal se escapa —tiene que escaparse, es
     * texto que viene de la base— y por lo tanto no admite HTML. Un pedido a
     * proveedor con diez productos puesto en una sola línea de texto no se
     * puede leer, y menos todavía imprimir.
     *
     * @param encabezados títulos de las columnas
     * @param filas       una lista de celdas por fila; deben tener tantas celdas como encabezados
     * @param pie         fila final resaltada (el total), o {@code null}
     */
    public record Tabla(List<String> encabezados, List<List<String>> filas, List<String> pie) {

        /** Columnas numéricas: se alinean a la derecha a partir de este índice. */
        public Tabla {
            Objects.requireNonNull(encabezados, "la tabla necesita encabezados");
            Objects.requireNonNull(filas, "la tabla necesita filas");
        }
    }

    /**
     * @param tipo             define el color de acento de la plantilla
     * @param tituloPrincipal  título mostrado debajo del header
     * @param nombreDestinatario nombre para el saludo ("Hola X,")
     * @param mensajePrincipal texto principal (puede tener varias oraciones)
     * @param datoDestacado    texto opcional resaltado en un recuadro (null para omitir)
     * @param textoBoton       texto del botón (null para omitir el botón)
     * @param urlBoton         destino del botón (ignorado si textoBoton es null)
     */
    public String construir(TipoCorreo tipo, String tituloPrincipal, String nombreDestinatario,
            String mensajePrincipal, String datoDestacado, String textoBoton, String urlBoton) {
        return construir(tipo, tituloPrincipal, nombreDestinatario, mensajePrincipal,
                datoDestacado, textoBoton, urlBoton, null);
    }

    /** Igual que el anterior, con una tabla de detalle entre el mensaje y el dato destacado. */
    public String construir(TipoCorreo tipo, String tituloPrincipal, String nombreDestinatario,
            String mensajePrincipal, String datoDestacado, String textoBoton, String urlBoton,
            Tabla tabla) {
        String color = tipo.getColorHex();
        String nombre = HtmlUtils.htmlEscape(nombreDestinatario);
        String mensaje = HtmlUtils.htmlEscape(mensajePrincipal).replace("\n", "<br>");

        StringBuilder html = new StringBuilder();
        html.append("""
                <html><body style="margin:0;padding:30px;background:#0F172A;font-family:Arial,sans-serif;">
                <table align="center" width="560" cellpadding="0" cellspacing="0"
                    style="background:#1E293B;border:1px solid %s;border-radius:14px;overflow:hidden;box-shadow:0 4px 12px rgba(0,0,0,0.3);">
                <tr><td align="center" style="padding:22px;background:#0F172A;border-bottom:2px solid %s;">
                <span style="font-size:20px;font-weight:bold;">
                <span style="color:%s;">⚡</span>
                <span style="color:#F1F5F9;">TechStore</span>
                </span>
                </td></tr>
                <tr><td style="padding:35px;">
                <h2 style="margin-top:0;color:%s;font-size:23px;">%s</h2>
                <p style="color:#CBD5E1;font-size:15px;line-height:1.7;">
                Hola <b>%s</b>,<br><br>%s</p>
                """.formatted(color, color, color, color, HtmlUtils.htmlEscape(tituloPrincipal), nombre, mensaje));

        if (tabla != null && !tabla.filas().isEmpty()) {
            html.append(renderizar(tabla, color));
        }

        if (datoDestacado != null && !datoDestacado.isBlank()) {
            html.append("""
                    <p style="background:#0F172A;padding:12px;border-left:3px solid %s;color:#CBD5E1;border-radius:4px;">
                    📌 %s</p>
                    """.formatted(color, HtmlUtils.htmlEscape(datoDestacado)));
        }

        if (textoBoton != null && !textoBoton.isBlank()) {
            html.append("""
                    <div style="text-align:center;margin:32px 0 8px;">
                    <a href="%s" style="background:%s;color:#0F172A;padding:14px 26px;text-decoration:none;
                    font-weight:bold;border-radius:8px;display:inline-block;font-size:14px;">%s</a>
                    </div>
                    """.formatted(HtmlUtils.htmlEscape(urlBoton), color, HtmlUtils.htmlEscape(textoBoton)));
        }

        boolean hayCasilla = empresa().email() != null && !empresa().email().isBlank();
        String pie;
        if (!tipo.esperaRespuesta()) {
            pie = "Este es un mensaje automático, por favor no respondas a este correo.";
        } else if (hayCasilla) {
            pie = "Podés responder este correo: la respuesta llega a "
                    + HtmlUtils.htmlEscape(empresa().email()) + ".";
        } else {
            pie = "Podés responder este correo.";
        }
        html.append("""
                </td></tr>
                <tr><td style="padding:0 35px 28px;">
                <p style="color:#94A3B8;font-size:12px;text-align:center;border-top:1px solid #334155;padding-top:18px;">
                %s<br>
                © %d %s — Todos los derechos reservados.</p>
                </td></tr>
                </table></body></html>
                """.formatted(pie, Year.now().getValue(), HtmlUtils.htmlEscape(empresa().nombre())));

        return html.toString();
    }

    /**
     * La tabla de detalle en HTML de correo: {@code <table>} con estilos en
     * línea y sin CSS externo, porque los clientes de correo descartan las hojas
     * de estilo. La primera columna va a la izquierda y las demás a la derecha,
     * que es como se leen cantidades e importes.
     */
    private String renderizar(Tabla tabla, String color) {
        StringBuilder t = new StringBuilder();
        t.append("""
                <table width="100%" cellpadding="8" cellspacing="0"
                    style="border-collapse:collapse;margin:20px 0;font-size:13px;">
                <tr>
                """);
        for (int i = 0; i < tabla.encabezados().size(); i++) {
            t.append("""
                    <td style="background:#0F172A;color:%s;font-weight:bold;text-align:%s;
                        border-bottom:2px solid %s;">%s</td>
                    """.formatted(color, i == 0 ? "left" : "right", color,
                    HtmlUtils.htmlEscape(tabla.encabezados().get(i))));
        }
        t.append("</tr>");

        for (List<String> fila : tabla.filas()) {
            t.append("<tr>");
            for (int i = 0; i < fila.size(); i++) {
                t.append("""
                        <td style="color:#CBD5E1;text-align:%s;border-bottom:1px solid #334155;">%s</td>
                        """.formatted(i == 0 ? "left" : "right", HtmlUtils.htmlEscape(fila.get(i))));
            }
            t.append("</tr>");
        }

        if (tabla.pie() != null && !tabla.pie().isEmpty()) {
            t.append("<tr>");
            for (int i = 0; i < tabla.pie().size(); i++) {
                t.append("""
                        <td style="color:#F1F5F9;font-weight:bold;text-align:%s;
                            border-top:2px solid %s;">%s</td>
                        """.formatted(i == 0 ? "left" : "right", color,
                        HtmlUtils.htmlEscape(tabla.pie().get(i))));
            }
            t.append("</tr>");
        }

        return t.append("</table>").toString();
    }
}
