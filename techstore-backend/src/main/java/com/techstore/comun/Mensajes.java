package com.techstore.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Locale;

/**
 * Resuelve las claves de {@code i18n/messages.properties} al idioma de quien
 * hizo la petición.
 *
 * <p>Existe para que el resto del código no tenga que arrastrar el
 * {@link MessageSource} ni preguntarse de dónde sale el idioma: el
 * {@code Accept-Language} de la petición lo deja puesto Spring en el
 * {@link LocaleContextHolder}, y acá se lee de ahí.
 *
 * <p>Si una clave no existe se devuelve la clave misma en vez de reventar. Un
 * texto sin traducir es un defecto cosmético; una excepción dentro del manejo
 * de errores esconde el error real, que es mucho peor.
 */
@Component
@RequiredArgsConstructor
public class Mensajes {

    private final MessageSource messageSource;

    /** El texto de la clave, en el idioma de la petición en curso. */
    public String de(String clave, Object... argumentos) {
        return de(LocaleContextHolder.getLocale(), clave, argumentos);
    }

    public String de(Locale idioma, String clave, Object... argumentos) {
        return messageSource.getMessage(clave, comoTexto(argumentos), clave, idioma);
    }

    /**
     * Pasa los enteros a texto antes de que los toque {@code MessageFormat}.
     *
     * <p>Son dos problemas de una vez. Uno: {@code MessageFormat} le aplica
     * formato de número a los enteros, y entonces el producto 999999 sale como
     * "999.999" —un id no es una cantidad y no lleva separador de miles—. El
     * otro: la alternativa habitual, escribir {@code {0,number,#}} en la
     * plantilla, obliga a que ese argumento sea siempre numérico, y el día que
     * alguien le pasa un nombre de producto revienta con
     * {@code IllegalArgumentException} justo mientras se armaba un mensaje de
     * error, que es el peor momento posible.
     *
     * <p>Convirtiendo acá, las plantillas se quedan solo con {@code {0}}, que
     * acepta cualquier cosa. Quien traduce no necesita saber MessageFormat.
     */
    private static Object[] comoTexto(Object[] argumentos) {
        if (argumentos == null) {
            return null;
        }
        Object[] convertidos = new Object[argumentos.length];
        for (int i = 0; i < argumentos.length; i++) {
            Object arg = argumentos[i];
            boolean entero = arg instanceof Long || arg instanceof Integer
                    || arg instanceof Short || arg instanceof Byte || arg instanceof BigInteger;
            convertidos[i] = entero ? arg.toString() : arg;
        }
        return convertidos;
    }
}
