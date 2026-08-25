package com.techstore.comun.web;

import com.techstore.comun.BusinessException;
import com.techstore.comun.ExcepcionTraducible;
import com.techstore.comun.Mensajes;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.usuarios.CuentaInactivaException;
import com.techstore.usuarios.TooManyAttemptsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Traduce los errores al idioma de quien hizo la petición y los devuelve como
 * {@code ProblemDetail}.
 *
 * <p>Es el único lugar donde se arma el texto que va a leer el usuario: las
 * excepciones viajan con la clave del mensaje ({@link ExcepcionTraducible}) y
 * recién acá se sabe qué idioma pidió el cliente.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Mensajes mensajes;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problema(HttpStatus.NOT_FOUND, "error.titulo.no.encontrado", ex);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleStaticNotFound(NoResourceFoundException ex) {
        return problema(HttpStatus.NOT_FOUND, "error.titulo.no.encontrado", "error.recurso.no.encontrado");
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        return problema(HttpStatus.CONFLICT, "error.titulo.regla.negocio", ex);
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ProblemDetail handleTooManyAttempts(TooManyAttemptsException ex) {
        return problema(HttpStatus.TOO_MANY_REQUESTS, "error.titulo.demasiados.intentos", ex);
    }

    @ExceptionHandler(CuentaInactivaException.class)
    public ProblemDetail handleCuentaInactiva(CuentaInactivaException ex) {
        return problema(HttpStatus.FORBIDDEN, "error.titulo.cuenta.bloqueada", ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // El texto de cada campo ya viene traducido: las anotaciones de
        // validación resuelven su mensaje contra el mismo messages.properties.
        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = problema(HttpStatus.BAD_REQUEST,
                "error.titulo.validacion", "error.validacion.campos.invalidos");
        problem.setProperty("errores", errores);
        return problem;
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return problema(HttpStatus.BAD_REQUEST, "error.titulo.ilegible", "error.cuerpo.ilegible");
    }

    /**
     * Errores de forma en parametros de URL: ausencia, tipo/formato invalido o
     * restricciones de Bean Validation. Son solicitudes invalidas, no fallos
     * internos del servidor.
     */
    @ExceptionHandler({
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            jakarta.validation.ConstraintViolationException.class
    })
    public ProblemDetail handleRequestParameterValidation(Exception ex) {
        return problema(HttpStatus.BAD_REQUEST,
                "error.titulo.validacion", "error.validacion.campos.invalidos");
    }

    /**
     * Credenciales inválidas. El detalle es siempre el mismo mensaje genérico y
     * no el de la excepción: distinguir "no existe el usuario" de "la
     * contraseña no coincide" le regala a quien prueba claves la mitad de la
     * respuesta.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return problema(HttpStatus.UNAUTHORIZED, "error.titulo.credenciales", "error.credenciales.invalidas");
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ProblemDetail handleArchivoDemasiadoGrande(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return problema(HttpStatus.PAYLOAD_TOO_LARGE, "error.titulo.archivo.grande", "error.archivo.demasiado.grande");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problema(HttpStatus.FORBIDDEN, "error.titulo.acceso.denegado", "error.sin.permisos");
    }

    /**
     * Errores de protocolo HTTP: el cliente pide un tipo que el endpoint no
     * produce, manda un cuerpo que no consume, o usa un metodo inexistente.
     * Sin estos handlers caen en el catch-all y salen como 500, lo que hace
     * parecer roto al servidor cuando el equivocado es el pedido.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ProblemDetail handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return problema(HttpStatus.NOT_ACCEPTABLE,
                "error.titulo.tipo.no.aceptable", "error.tipo.no.aceptable");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "error.titulo.tipo.no.soportado", "error.tipo.no.soportado");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return problema(HttpStatus.METHOD_NOT_ALLOWED,
                "error.titulo.metodo.no.permitido", "error.metodo.no.permitido");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Error no controlado", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "error.titulo.interno", "error.interno");
    }

    private ProblemDetail problema(HttpStatus estado, String claveTitulo, ExcepcionTraducible ex) {
        return problema(estado, claveTitulo, ex.getClave(), ex.getArgumentos());
    }

    private ProblemDetail problema(HttpStatus estado, String claveTitulo, String claveDetalle, Object... argumentos) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(estado, mensajes.de(claveDetalle, argumentos));
        problem.setTitle(mensajes.de(claveTitulo));
        return problem;
    }
}
