package com.techstore.empresa;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.comun.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
    private final AuditoriaService auditoriaService;
    private final Environment environment;

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

    /**
     * Los mismos datos, pero exigiendo que estén completos. Lo usa la emisión:
     * una factura con el timbrado o la dirección en blanco no sirve, y el
     * problema recién se vería en el papel, con el número ya consumido.
     */
    @Transactional(readOnly = true)
    public EmpresaProperties actualParaFacturar() {
        EmpresaProperties emisor = actual();
        List<String> faltantes = new ArrayList<>(DatosDelEmisor.faltantes(emisor));
        if (DatosDelEmisor.faltaTipoContribuyente(emisor)) {
            faltantes.add("Tipo de contribuyente");
        }
        if (esProduccion() && DatosDelEmisor.contieneDatosDemo(emisor)) {
            faltantes.add("Reemplaza el RUC, timbrado y correo de demostración");
        }
        if (!faltantes.isEmpty()) {
            throw new BusinessException("empresa.datos.incompletos", String.join(", ", faltantes));
        }
        return emisor;
    }

    /** Qué falta cargar para poder facturar. Vacío = está todo. */
    @Transactional(readOnly = true)
    public DatosEmisorPendientesResponse pendientes() {
        EmpresaProperties emisor = actual();
        List<String> etiquetas = new ArrayList<>(DatosDelEmisor.faltantes(emisor));
        List<String> campos = new ArrayList<>(DatosDelEmisor.camposFaltantes(emisor));
        if (DatosDelEmisor.faltaTipoContribuyente(emisor)) {
            etiquetas.add("Tipo de contribuyente");
            campos.add("tipoContribuyente");
        }
        if (esProduccion() && DatosDelEmisor.contieneDatosDemo(emisor)) {
            etiquetas.add("Reemplaza los datos fiscales de demostración");
            campos.add("ruc");
        }
        return new DatosEmisorPendientesResponse(etiquetas.isEmpty(), etiquetas, campos);
    }

    @Transactional
    public EmpresaProperties guardar(EmpresaRequest req) {
        // Estos datos salen impresos en cada factura: cambiarlos es de lo más
        // sensible que se puede hacer en el sistema, así que se audita el antes
        // y el después de lo que identifica al emisor ante la DNIT.
        EmpresaProperties anterior = actual();

        set("nombre", req.nombre());
        set("ruc", req.ruc());
        set("direccion", req.direccion());
        set("ciudad", req.ciudad());
        set("telefono", req.telefono());
        set("email", req.email());
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
        set("identificacion-sin-nombre", req.identificacionSinNombre());

        cache = null;
        EmpresaProperties nueva = actual();

        auditoriaService.registrar("Empresa", null, AuditoriaService.Accion.Editar,
                "Cambió los datos del emisor de las facturas",
                AuditoriaService.Cambios.nuevo()
                        .de("nombre", anterior.nombre(), nueva.nombre())
                        .de("ruc", anterior.ruc(), nueva.ruc())
                        .de("timbrado", anterior.timbrado(), nueva.timbrado())
                        .de("timbradoVigencia", anterior.timbradoVigencia(), nueva.timbradoVigencia())
                        .de("establecimiento", anterior.establecimiento(), nueva.establecimiento())
                        .de("puntoExpedicion", anterior.puntoExpedicion(), nueva.puntoExpedicion())
                        .de("direccion", anterior.direccion(), nueva.direccion())
                        .de("telefono", anterior.telefono(), nueva.telefono()));

        return nueva;
    }


    /**
     * Dónde queda la empresa.
     *
     * <p>Vive junto a los datos del emisor porque se administra en la misma
     * pantalla, pero no es dato fiscal: no sale impresa en la factura ni viaja
     * en el Documento Electrónico. Por eso se guarda aparte de
     * {@link EmpresaProperties} en lugar de engrosar el emisor.
     */
    @Transactional(readOnly = true)
    public UbicacionEmpresaResponse ubicacion() {
        String latitud = valor("latitud", null);
        String longitud = valor("longitud", null);
        if (latitud == null || longitud == null) {
            return UbicacionEmpresaResponse.sinDefinir();
        }
        try {
            return new UbicacionEmpresaResponse(true,
                    new java.math.BigDecimal(latitud),
                    new java.math.BigDecimal(longitud),
                    valor("ubicacion-referencia", null));
        } catch (NumberFormatException guardadoInvalido) {
            // Un valor corrupto en la tabla no debe romper la pantalla entera:
            // se muestra como «sin ubicación» y se puede volver a marcar.
            return UbicacionEmpresaResponse.sinDefinir();
        }
    }

    @Transactional
    public UbicacionEmpresaResponse guardarUbicacion(UbicacionEmpresaRequest req) {
        UbicacionEmpresaResponse anterior = ubicacion();
        set("latitud", req.latitud().toPlainString());
        set("longitud", req.longitud().toPlainString());
        set("ubicacion-referencia", req.referencia());
        cache = null;

        auditoriaService.registrar("Empresa", null, AuditoriaService.Accion.Editar,
                "Cambió la ubicación de la empresa",
                AuditoriaService.Cambios.nuevo()
                        .de("latitud", texto(anterior.latitud()), req.latitud().toPlainString())
                        .de("longitud", texto(anterior.longitud()), req.longitud().toPlainString())
                        .de("referencia", anterior.referencia(), req.referencia()));

        return ubicacion();
    }

    private static String texto(java.math.BigDecimal valor) {
        return valor == null ? null : valor.toPlainString();
    }
    private EmpresaProperties resolver() {
        return new EmpresaProperties(
                valor("nombre", porDefecto.nombre()),
                valor("ruc", porDefecto.ruc()),
                valor("direccion", porDefecto.direccion()),
                valor("ciudad", porDefecto.ciudad()),
                valor("telefono", porDefecto.telefono()),
                valor("email", porDefecto.email()),
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
                valor("timbrado-inicio-vigencia", porDefecto.timbradoInicioVigencia()),
                valor("identificacion-sin-nombre", porDefecto.identificacionSinNombre()));
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

    private boolean esProduccion() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    /** Vacío borra el valor guardado, o sea que vuelve al de application.properties. */
    private void set(String clave, String valor) {
        String id = PREFIJO + clave;
        Configuracion c = repo.findById(id).orElseGet(() -> new Configuracion(id, null));
        c.setValor(valor == null || valor.isBlank() ? null : valor.trim());
        repo.save(c);
    }
}
