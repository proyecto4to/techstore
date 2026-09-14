package com.techstore.auditoria;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Desde dónde se hizo la operación.
 *
 * <p>Importa en la auditoría porque «el cajero anuló una factura» no es lo
 * mismo desde el mostrador que desde un teléfono a las tres de la mañana. Se
 * deduce del pedido en curso y no se le pide a nadie que la pase: un dato que
 * cada llamador tiene que acordarse de completar termina llegando vacío.
 *
 * <p>Se mira primero la cabecera que los clientes propios declaran, y solo si
 * no viene se cae al {@code User-Agent}. El orden es a propósito: el
 * {@code User-Agent} se puede falsificar, pero también la cabecera, así que
 * esto describe lo que dijo el cliente, no una verdad comprobada. Para decidir
 * permisos no se usa; para leer un registro, alcanza.
 */
public enum Plataforma {

    WEB("Web"),
    MOVIL("Movil"),
    API("Api");

    /** Lo que los clientes propios mandan para identificarse. */
    public static final String CABECERA = "X-Plataforma";

    private final String etiqueta;

    Plataforma(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** La del pedido que se está atendiendo, o nulo si no hay ninguno (una tarea del sistema). */
    public static String actual() {
        HttpServletRequest pedido = pedidoEnCurso();
        if (pedido == null) {
            return null;
        }
        return de(pedido).etiqueta();
    }

    public static Plataforma de(HttpServletRequest pedido) {
        String declarada = pedido.getHeader(CABECERA);
        if (declarada != null && !declarada.isBlank()) {
            for (Plataforma plataforma : values()) {
                if (plataforma.etiqueta.equalsIgnoreCase(declarada.trim())) {
                    return plataforma;
                }
            }
        }
        String agente = pedido.getHeader("User-Agent");
        if (agente == null || agente.isBlank()) {
            return API;
        }
        String enMinusculas = agente.toLowerCase();
        // Dio usa Dart en Android; okhttp aparece cuando el pedido pasa por el
        // cliente nativo. Cualquiera de los dos es la app.
        if (enMinusculas.contains("dart") || enMinusculas.contains("okhttp")
                || enMinusculas.contains("android")) {
            return MOVIL;
        }
        return enMinusculas.contains("mozilla") ? WEB : API;
    }

    private static HttpServletRequest pedidoEnCurso() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest();
        }
        return null;
    }
}
