package com.techstore.configuracion;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Guarda de zona horaria")
class ZonaHorariaGuardTest {

    private ListAppender<ILoggingEvent> registro;
    private Logger logger;
    private TimeZone original;

    @BeforeEach
    void preparar() {
        original = TimeZone.getDefault();
        logger = (Logger) LoggerFactory.getLogger(ZonaHorariaGuard.class);
        registro = new ListAppender<>();
        registro.start();
        logger.addAppender(registro);
    }

    @AfterEach
    void restaurar() {
        logger.detachAppender(registro);
        TimeZone.setDefault(original);
    }

    @Test
    void avisaCuandoElRelojNoEstaEnLaHoraLegal() {
        // Es el caso real: el JDK con base de zonas vieja resuelve
        // America/Asuncion a -04:00 y el sistema graba una hora menos.
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Etc/GMT+4")));

        new ZonaHorariaGuard("-03:00").run(null);

        assertEquals(1, registro.list.size(), "Tenía que decir algo");
        ILoggingEvent evento = registro.list.getFirst();
        assertEquals(Level.ERROR, evento.getLevel());
        String mensaje = evento.getFormattedMessage();
        assertTrue(mensaje.contains("-04:00"), "No dijo qué desfase encontró");
        assertTrue(mensaje.contains("-03:00"), "No dijo cuál esperaba");
        // El aviso tiene que traer el arreglo, no solo la queja.
        assertTrue(mensaje.contains("user.timezone"), "No dijo cómo arreglarlo");
    }

    @Test
    void noMolestaCuandoLaHoraEsLaCorrecta() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Etc/GMT+3")));

        new ZonaHorariaGuard("-03:00").run(null);

        assertEquals(1, registro.list.size());
        assertEquals(Level.INFO, registro.list.getFirst().getLevel(),
                "Con la hora bien no corresponde un error");
    }

    @Test
    void elDesfaseEsperadoEsConfigurable() {
        // Si algún día el país vuelve a mover el reloj, se cambia la propiedad
        // y no el código.
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Etc/GMT+4")));

        new ZonaHorariaGuard("-04:00").run(null);

        assertEquals(Level.INFO, registro.list.getFirst().getLevel());
    }
}
