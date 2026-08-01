package com.techstore.comun.web;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Hace que los mensajes de las anotaciones de validación salgan del mismo
 * {@code i18n/messages.properties} que el resto de los textos.
 *
 * <p>Sin esto, Bean Validation busca sus textos en un
 * {@code ValidationMessages.properties} propio, en la raíz del classpath: dos
 * archivos separados para lo mismo, y el de validación fuera del alcance del
 * idioma que negocia Spring.
 */
@Configuration
public class ValidacionI18nConfig {

    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.setValidationMessageSource(messageSource);
        return validador;
    }
}
