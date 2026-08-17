package com.techstore.notificaciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExpoPushProviderTest {

    /**
     * El proveedor tiene dos constructores: el de producción y uno de paquete que
     * recibe el cliente HTTP para las pruebas. Con más de uno, Spring no elige
     * solo: busca el constructor vacío, no lo encuentra y la aplicación entera no
     * arranca.
     *
     * <p>El defecto no se ve mientras el push está apagado, porque el bean sólo se
     * crea con {@code push.provider=expo}: queda dormido hasta el día que alguien
     * enciende las notificaciones, y entonces lo que falla no es el push sino el
     * arranque completo.
     */
    @Test
    void springLoInstanciaAunqueTengaDosConstructores() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // El bean sólo existe con el proveedor encendido; sin esta propiedad la
            // condición lo descarta y no se estaría probando nada.
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("push", Map.of("push.provider", "expo")));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(PushProperties.class, () -> new PushProperties(
                    "expo", URI.create("https://exp.host/--/api/v2/push/send"), ""));
            context.register(ExpoPushProvider.class);

            context.refresh();

            assertNotNull(context.getBean(ExpoPushProvider.class));
        }
    }
}
