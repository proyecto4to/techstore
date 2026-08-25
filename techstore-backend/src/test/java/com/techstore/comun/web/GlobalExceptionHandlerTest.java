package com.techstore.comun.web;

import com.techstore.comun.Mensajes;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void parametroObligatorioAusenteEs400YNo500() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.titulo.validacion", Locale.getDefault(), "Error de validacion");
        source.addMessage("error.validacion.campos.invalidos", Locale.getDefault(), "Solicitud invalida");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new Mensajes(source));

        ProblemDetail result = handler.handleRequestParameterValidation(
                new MissingServletRequestParameterException("desde", "LocalDate"));

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.getStatus());
        assertEquals("Error de validacion", result.getTitle());
        assertEquals("Solicitud invalida", result.getDetail());
    }

    /**
     * El cliente movil pedia el PDF de una factura con Accept: application/json
     * y recibia un 500, que hacia parecer roto al servidor cuando el pedido era
     * el equivocado. Estas tres son errores del pedido, no del servidor.
     */
    @Test
    void acceptIncompatibleEs406YNo500() {
        GlobalExceptionHandler handler = handlerCon(
                "error.titulo.tipo.no.aceptable", "Tipo no aceptable",
                "error.tipo.no.aceptable", "Formato no disponible");

        ProblemDetail result = handler.handleMediaTypeNotAcceptable(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_PDF)));

        assertEquals(HttpStatus.NOT_ACCEPTABLE.value(), result.getStatus());
        assertEquals("Tipo no aceptable", result.getTitle());
        assertEquals("Formato no disponible", result.getDetail());
    }

    @Test
    void contentTypeIncompatibleEs415YNo500() {
        GlobalExceptionHandler handler = handlerCon(
                "error.titulo.tipo.no.soportado", "Tipo no soportado",
                "error.tipo.no.soportado", "Contenido no soportado");

        ProblemDetail result = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN,
                        List.of(MediaType.APPLICATION_JSON)));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), result.getStatus());
        assertEquals("Tipo no soportado", result.getTitle());
        assertEquals("Contenido no soportado", result.getDetail());
    }

    @Test
    void metodoNoPermitidoEs405YNo500() {
        GlobalExceptionHandler handler = handlerCon(
                "error.titulo.metodo.no.permitido", "Metodo no permitido",
                "error.metodo.no.permitido", "Metodo no habilitado");

        ProblemDetail result = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("POST")));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), result.getStatus());
        assertEquals("Metodo no permitido", result.getTitle());
        assertEquals("Metodo no habilitado", result.getDetail());
    }

    private GlobalExceptionHandler handlerCon(String claveTitulo, String titulo,
                                              String claveDetalle, String detalle) {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage(claveTitulo, Locale.getDefault(), titulo);
        source.addMessage(claveDetalle, Locale.getDefault(), detalle);
        return new GlobalExceptionHandler(new Mensajes(source));
    }
}
