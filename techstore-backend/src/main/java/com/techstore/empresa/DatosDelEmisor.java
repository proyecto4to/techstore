package com.techstore.empresa;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Qué datos del emisor tienen que estar cargados para poder emitir una factura.
 *
 * <p>Sin ellos el documento sale con huecos: un timbrado en blanco, una
 * dirección vacía, un código de actividad que el SIFEN no puede validar. El
 * problema es que eso no se nota al configurar sino al mirar una factura ya
 * emitida, cuando el número de timbrado ya se consumió.
 *
 * <p>Por eso la lista vive acá y la usan dos lugares: el panel, para avisar
 * antes de que alguien intente vender, y la emisión, para no dejar salir un
 * documento incompleto.
 */
public final class DatosDelEmisor {

    /**
     * Campo obligatorio: cómo se lee del emisor y con qué nombre se le pide al
     * usuario que lo complete.
     */
    private record Obligatorio(String etiqueta, Function<EmpresaProperties, String> valor) {
    }

    private static final Map<String, Obligatorio> OBLIGATORIOS = new LinkedHashMap<>();

    static {
        // El orden es el que tiene la pantalla Admin → Empresa, para que la
        // lista de faltantes se lea de arriba abajo como el formulario.
        obligatorio("nombre", "Nombre o razón social", EmpresaProperties::nombre);
        obligatorio("ruc", "RUC", EmpresaProperties::ruc);
        obligatorio("direccion", "Dirección", EmpresaProperties::direccion);
        obligatorio("ciudad", "Ciudad", EmpresaProperties::ciudad);
        obligatorio("telefono", "Teléfono", EmpresaProperties::telefono);
        obligatorio("email", "Correo electrónico", EmpresaProperties::email);
        obligatorio("actividad", "Actividad económica", EmpresaProperties::actividad);
        obligatorio("timbrado", "Número de timbrado", EmpresaProperties::timbrado);
        obligatorio("timbradoInicioVigencia", "Inicio de vigencia del timbrado",
                EmpresaProperties::timbradoInicioVigencia);
        obligatorio("establecimiento", "Establecimiento", EmpresaProperties::establecimiento);
        obligatorio("puntoExpedicion", "Punto de expedición", EmpresaProperties::puntoExpedicion);
        // Los que viajan en el Documento Electrónico y el SIFEN valida contra
        // sus tablas: sin ellos el XML se arma pero el documento no es válido.
        obligatorio("actividadCodigo", "Código de actividad económica", EmpresaProperties::actividadCodigo);
        obligatorio("departamentoCodigo", "Código de departamento", EmpresaProperties::departamentoCodigo);
        obligatorio("departamentoNombre", "Departamento", EmpresaProperties::departamentoNombre);
        obligatorio("distritoCodigo", "Código de distrito", EmpresaProperties::distritoCodigo);
        obligatorio("distritoNombre", "Distrito", EmpresaProperties::distritoNombre);
        obligatorio("ciudadCodigo", "Código de ciudad", EmpresaProperties::ciudadCodigo);
        obligatorio("ciudadNombre", "Ciudad (SIFEN)", EmpresaProperties::ciudadNombre);
    }

    private DatosDelEmisor() {
    }

    private static void obligatorio(String campo, String etiqueta, Function<EmpresaProperties, String> valor) {
        OBLIGATORIOS.put(campo, new Obligatorio(etiqueta, valor));
    }

    /**
     * Los campos que están sin cargar, con el nombre que ve el usuario. Lista
     * vacía significa que se puede facturar.
     */
    public static List<String> faltantes(EmpresaProperties emisor) {
        return OBLIGATORIOS.values().stream()
                .filter(campo -> vacio(campo.valor().apply(emisor)))
                .map(Obligatorio::etiqueta)
                .toList();
    }

    /** Los nombres internos de los campos faltantes, para que el panel los marque. */
    public static List<String> camposFaltantes(EmpresaProperties emisor) {
        return OBLIGATORIOS.entrySet().stream()
                .filter(e -> vacio(e.getValue().valor().apply(emisor)))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * El tipo de contribuyente es numérico, así que no entra en la lista de
     * arriba, pero también tiene que estar.
     */
    public static boolean faltaTipoContribuyente(EmpresaProperties emisor) {
        return emisor.tipoContribuyente() == null;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
