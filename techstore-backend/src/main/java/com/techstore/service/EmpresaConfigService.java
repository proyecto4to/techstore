package com.techstore.service;

import com.techstore.config.EmpresaProperties;
import com.techstore.dto.request.EmpresaRequest;
import com.techstore.entity.Configuracion;
import com.techstore.repository.ConfiguracionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos del emisor (los que salen en la factura y en el Documento Electrónico),
 * editables desde la app. Se guardan en la tabla {@code configuracion} y lo que
 * no esté cargado cae a los valores de {@code application.properties}, así el
 * sistema arranca igual que siempre hasta que alguien los edite.
 *
 * <p>Se cachea el resultado porque el KuDE y el XML lo consultan muchas veces
 * por documento; el caché se descarta al guardar.
 */
@Service
@RequiredArgsConstructor
public class EmpresaConfigService {

    private static final String PREFIJO = "empresa.";

    private final ConfiguracionRepository repo;
    /** Valores de application.properties: el punto de partida si no hay nada en la base. */
    private final EmpresaProperties porDefecto;

    private volatile EmpresaProperties cache;

    /** Datos vigentes del emisor. */
    @Transactional(readOnly = true)
    public EmpresaProperties actual() {
        EmpresaProperties vigente = cache;
        if (vigente == null) {
            vigente = resolver();
            cache = vigente;
        }
        return vigente;
    }

    @Transactional
    public EmpresaProperties guardar(EmpresaRequest req) {
        set("nombre", req.nombre());
        set("ruc", req.ruc());
        set("direccion", req.direccion());
        set("ciudad", req.ciudad());
        set("telefono", req.telefono());
        set("actividad", req.actividad());
        set("timbrado", req.timbrado());
        set("timbrado-vigencia", req.timbradoVigencia());
        set("timbrado-inicio-vigencia", req.timbradoInicioVigencia());
        set("tipo-contribuyente", req.tipoContribuyente() == null
                ? null : String.valueOf(req.tipoContribuyente()));
        set("establecimiento", req.establecimiento());
        set("punto-expedicion", req.puntoExpedicion());
        set("actividad-codigo", req.actividadCodigo());
        set("departamento-codigo", req.departamentoCodigo());
        set("departamento-nombre", req.departamentoNombre());
        set("distrito-codigo", req.distritoCodigo());
        set("distrito-nombre", req.distritoNombre());
        set("ciudad-codigo", req.ciudadCodigo());
        set("ciudad-nombre", req.ciudadNombre());

        cache = null;
        return actual();
    }

    private EmpresaProperties resolver() {
        return new EmpresaProperties(
                valor("nombre", porDefecto.nombre()),
                valor("ruc", porDefecto.ruc()),
                valor("direccion", porDefecto.direccion()),
                valor("ciudad", porDefecto.ciudad()),
                valor("telefono", porDefecto.telefono()),
                valor("actividad", porDefecto.actividad()),
                valor("timbrado", porDefecto.timbrado()),
                valor("timbrado-vigencia", porDefecto.timbradoVigencia()),
                entero("tipo-contribuyente", porDefecto.tipoContribuyente()),
                valor("establecimiento", porDefecto.establecimiento()),
                valor("punto-expedicion", porDefecto.puntoExpedicion()),
                valor("actividad-codigo", porDefecto.actividadCodigo()),
                valor("departamento-codigo", porDefecto.departamentoCodigo()),
                valor("departamento-nombre", porDefecto.departamentoNombre()),
                valor("distrito-codigo", porDefecto.distritoCodigo()),
                valor("distrito-nombre", porDefecto.distritoNombre()),
                valor("ciudad-codigo", porDefecto.ciudadCodigo()),
                valor("ciudad-nombre", porDefecto.ciudadNombre()),
                valor("timbrado-inicio-vigencia", porDefecto.timbradoInicioVigencia()));
    }

    private String valor(String clave, String porDefectoValor) {
        return repo.findById(PREFIJO + clave)
                .map(Configuracion::getValor)
                .filter(v -> v != null && !v.isBlank())
                .orElse(porDefectoValor);
    }

    private Integer entero(String clave, Integer porDefectoValor) {
        String v = valor(clave, null);
        if (v == null) {
            return porDefectoValor;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException ex) {
            return porDefectoValor;
        }
    }

    /** Vacío borra el valor guardado, o sea que vuelve al de application.properties. */
    private void set(String clave, String valor) {
        String id = PREFIJO + clave;
        Configuracion c = repo.findById(id).orElseGet(() -> new Configuracion(id, null));
        c.setValor(valor == null || valor.isBlank() ? null : valor.trim());
        repo.save(c);
    }
}
