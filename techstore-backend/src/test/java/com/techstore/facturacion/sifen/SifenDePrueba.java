package com.techstore.facturacion.sifen;

/**
 * Configuración SIFEN para los tests, equivalente al bloque {@code sifen.*} que
 * trae {@code application.properties} en la instalación demostrativa.
 *
 * <p>Vive acá y no dentro de un test concreto para que exista un solo lugar
 * donde estén los valores del ambiente de prueba de la DNIT: el CSC de ejemplo,
 * las URLs {@code -test} y el keystore autofirmado que usa
 * {@link FirmaDigitalService}. Un test que arme su propia versión terminaría
 * validando contra parámetros que la aplicación real no usa.
 */
public final class SifenDePrueba {

    /** CSC de ejemplo del manual técnico. No es un código habilitado. */
    public static final String CSC = "ABCD0000000000000000000000000000";
    public static final String ID_CSC = "0001";
    public static final String QR_BASE_URL = "https://ekuatia.set.gov.py/consultas-test/qr";
    public static final String CONSULTA_PUBLICA_URL = "https://ekuatia.set.gov.py/consultas-test/de";

    private SifenDePrueba() {
    }

    /**
     * Modo TEST con la marca {@code datosDemo}: los valores de arriba contienen
     * "test" y el CSC es el de ejemplo, así que {@link SifenProperties#validar()}
     * exige que el uso demostrativo esté declarado a propósito.
     */
    public static SifenProperties test() {
        SifenProperties props = new SifenProperties();
        props.setModo(SifenProperties.Modo.TEST);
        props.setVersion("150");
        props.setDatosDemo(true);

        props.getFirma().setUbicacion("classpath:/sifen/techstore-fe-test.p12");
        props.getFirma().setPassword("changeit");
        props.getFirma().setAlias("techstore-fe");

        props.getQr().setBaseUrl(QR_BASE_URL);
        props.getQr().setConsultaPublicaUrl(CONSULTA_PUBLICA_URL);
        props.getQr().setCsc(CSC);
        props.getQr().setIdCsc(ID_CSC);

        SifenProperties.Endpoints endpoints = props.getEndpoints();
        endpoints.setRecepcionSincrona("https://sifen-test.set.gov.py/de/ws/sync/recibe.wsdl");
        endpoints.setRecepcionLote("https://sifen-test.set.gov.py/de/ws/async/recibe-lote.wsdl");
        endpoints.setConsultaLote("https://sifen-test.set.gov.py/de/ws/consultas/consulta-lote.wsdl");
        endpoints.setConsultaDe("https://sifen-test.set.gov.py/de/ws/consultas/consulta.wsdl");
        endpoints.setConsultaRuc("https://sifen-test.set.gov.py/de/ws/consultas/consulta-ruc.wsdl");
        endpoints.setEventos("https://sifen-test.set.gov.py/de/ws/eventos/evento.wsdl");

        props.validar();
        return props;
    }
}
