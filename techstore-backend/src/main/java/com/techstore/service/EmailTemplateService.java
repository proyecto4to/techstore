package com.techstore.service;

import com.techstore.config.EmpresaProperties;
import com.techstore.entity.TipoCorreo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Year;

/**
 * Arma el HTML de los correos de TechStore con una plantilla única (header con
 * marca, título coloreado según el {@link TipoCorreo}, mensaje, dato destacado
 * opcional y botón de acción opcional). Mismo estilo visual que la app: fondo
 * oscuro y colores de Tailwind (ver tailwind.config.js).
 */
@Service
public class EmailTemplateService {

    private final EmpresaProperties empresa;
    private final String frontendUrl;

    public EmailTemplateService(EmpresaProperties empresa, @Value("${app.frontend-url}") String frontendUrl) {
        this.empresa = empresa;
        this.frontendUrl = frontendUrl;
    }

    /** URL absoluta de la tienda a partir de una ruta relativa (ej. "/mi-cuenta"). */
    public String url(String rutaRelativa) {
        return frontendUrl.replaceAll("/+$", "") + rutaRelativa;
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

        html.append("""
                </td></tr>
                <tr><td style="padding:0 35px 28px;">
                <p style="color:#94A3B8;font-size:12px;text-align:center;border-top:1px solid #334155;padding-top:18px;">
                Este es un mensaje automático, por favor no respondas a este correo.<br>
                © %d %s — Todos los derechos reservados.</p>
                </td></tr>
                </table></body></html>
                """.formatted(Year.now().getValue(), HtmlUtils.htmlEscape(empresa.nombre())));

        return html.toString();
    }
}
