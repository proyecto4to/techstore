package com.techstore.facturacion.sifen;

import com.techstore.comun.BusinessException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Configuración fail-closed del generador SIFEN.
 *
 * <p>Esta clase no implementa transporte hacia la DNIT. Centraliza únicamente
 * el ambiente, las credenciales de firma/QR y las direcciones que necesitará
 * un cliente futuro, evitando constantes sensibles dentro del código.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sifen")
public class SifenProperties implements InitializingBean {

    private static final Pattern CSC = Pattern.compile("[A-Za-z0-9]{32}");
    private static final Pattern ID_CSC = Pattern.compile("[0-9]{1,4}");

    public enum Modo {
        OFF,
        TEST,
        PROD
    }

    private Modo modo = Modo.OFF;
    private String version = "150";
    /** Marca explícita que autoriza credenciales y URLs demostrativas en TEST. */
    private boolean datosDemo;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(30);
    private final Firma firma = new Firma();
    private final Qr qr = new Qr();
    private final Endpoints endpoints = new Endpoints();

    @Getter
    @Setter
    public static class Firma {
        /** Recurso Spring, por ejemplo file:/run/secrets/sifen.p12 o classpath:... solo en TEST. */
        private String ubicacion;
        private String password;
        private String alias;
    }

    @Getter
    @Setter
    public static class Qr {
        private String baseUrl;
        private String consultaPublicaUrl;
        private String csc;
        private String idCsc;
    }

    @Getter
    @Setter
    public static class Endpoints {
        private String recepcionSincrona;
        private String recepcionLote;
        private String consultaLote;
        private String consultaDe;
        private String consultaRuc;
        private String eventos;

        private List<String> todos() {
            return Arrays.asList(
                    recepcionSincrona,
                    recepcionLote,
                    consultaLote,
                    consultaDe,
                    consultaRuc,
                    eventos);
        }
    }

    @Override
    public void afterPropertiesSet() {
        validar();
    }

    public boolean activo() {
        return modo != Modo.OFF;
    }

    public boolean produccion() {
        return modo == Modo.PROD;
    }

    /** Bloquea cualquier salida SIFEN cuando el modo está apagado. */
    public void exigirActivo() {
        if (!activo()) {
            throw new BusinessException("sifen.deshabilitado");
        }
        validar();
    }

    /**
     * Valida también fuera de Spring para que los tests y usos manuales tengan
     * exactamente las mismas barreras que el arranque de la aplicación.
     */
    public void validar() {
        if (modo == null) {
            throw invalida("falta sifen.modo");
        }
        if (modo == Modo.OFF) {
            return;
        }

        requerido(version, "sifen.version");
        if (!"150".equals(version.trim())) {
            throw invalida("esta versión del generador solo admite sifen.version=150");
        }
        positivo(connectTimeout, "sifen.connect-timeout");
        positivo(readTimeout, "sifen.read-timeout");

        requerido(firma.ubicacion, "sifen.firma.ubicacion");
        requerido(firma.password, "sifen.firma.password");
        requerido(firma.alias, "sifen.firma.alias");
        requerido(qr.baseUrl, "sifen.qr.base-url");
        requerido(qr.consultaPublicaUrl, "sifen.qr.consulta-publica-url");
        requerido(qr.csc, "sifen.qr.csc");
        requerido(qr.idCsc, "sifen.qr.id-csc");
        if (!CSC.matcher(qr.csc.trim()).matches()) {
            throw invalida("sifen.qr.csc debe tener 32 caracteres alfanuméricos");
        }
        if (!ID_CSC.matcher(qr.idCsc.trim()).matches()) {
            throw invalida("sifen.qr.id-csc debe contener entre 1 y 4 dígitos");
        }

        urlHttps(qr.baseUrl, "sifen.qr.base-url");
        urlHttps(qr.consultaPublicaUrl, "sifen.qr.consulta-publica-url");
        String[] nombres = {
                "sifen.endpoints.recepcion-sincrona",
                "sifen.endpoints.recepcion-lote",
                "sifen.endpoints.consulta-lote",
                "sifen.endpoints.consulta-de",
                "sifen.endpoints.consulta-ruc",
                "sifen.endpoints.eventos"
        };
        List<String> urls = endpoints.todos();
        for (int i = 0; i < urls.size(); i++) {
            requerido(urls.get(i), nombres[i]);
            urlHttps(urls.get(i), nombres[i]);
        }

        boolean marcadorDemo = tieneMarcadoresDemo();
        if (modo == Modo.TEST && marcadorDemo && !datosDemo) {
            throw invalida("los valores demostrativos en TEST requieren sifen.datos-demo=true");
        }
        if (modo == Modo.PROD) {
            if (datosDemo) {
                throw invalida("sifen.datos-demo no puede estar habilitado en PROD");
            }
            if (marcadorDemo) {
                throw invalida("PROD contiene una URL, CSC, alias o certificado de TEST/demostración");
            }
            if (firma.ubicacion.trim().toLowerCase(Locale.ROOT).startsWith("classpath:")) {
                throw invalida("el certificado de PROD debe ser externo al artefacto, no classpath:");
            }
        }
    }

    private boolean tieneMarcadoresDemo() {
        if (noProductivo(firma.ubicacion) || noProductivo(firma.alias)
                || cscDemostrativo(qr.csc) || noProductivo(qr.baseUrl)
                || noProductivo(qr.consultaPublicaUrl)) {
            return true;
        }
        return endpoints.todos().stream().anyMatch(SifenProperties::noProductivo);
    }

    private static boolean cscDemostrativo(String valor) {
        if (valor == null) {
            return false;
        }
        String limpio = valor.trim().toUpperCase(Locale.ROOT);
        return limpio.contains("TEST")
                || limpio.contains("DEMO")
                || limpio.matches("(ABCD|EFGH)0{28}");
    }

    private static boolean noProductivo(String valor) {
        if (valor == null) {
            return false;
        }
        String limpio = valor.trim().toLowerCase(Locale.ROOT);
        return limpio.contains("test")
                || limpio.contains("demo")
                || limpio.contains("localhost")
                || limpio.contains("127.0.0.1");
    }

    private static void requerido(String valor, String propiedad) {
        if (valor == null || valor.isBlank()) {
            throw invalida("falta " + propiedad);
        }
    }

    private static void positivo(Duration valor, String propiedad) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw invalida(propiedad + " debe ser mayor que cero");
        }
    }

    private static void urlHttps(String valor, String propiedad) {
        try {
            URI uri = URI.create(valor.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw invalida(propiedad + " debe ser una URL HTTPS absoluta");
            }
        } catch (IllegalArgumentException ex) {
            throw invalida(propiedad + " no es una URL válida");
        }
    }

    private static IllegalStateException invalida(String detalle) {
        return new IllegalStateException("Configuración SIFEN inválida: " + detalle);
    }
}
