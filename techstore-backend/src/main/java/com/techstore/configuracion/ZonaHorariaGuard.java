package com.techstore.configuracion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Avisa si el reloj del sistema no está en la hora legal del país.
 *
 * <p>Existe por un problema real y silencioso: el JDK 22 trae la base de zonas
 * {@code 2024a}, que todavía resuelve {@code America/Asuncion} a
 * {@code -04:00}. Paraguay está en {@code -03:00} permanente desde 2024, así
 * que {@code LocalDateTime.now()} devolvía una hora menos y el sistema grababa
 * cada pedido, factura y cierre de caja con la hora corrida. No fallaba nada:
 * simplemente los papeles decían otra hora.
 *
 * <p>El arreglo vive en el script de arranque, que fija
 * {@code -Duser.timezone=Etc/GMT+3}. Esta guarda está para que ese flag no se
 * pierda sin que nadie se entere: si el desfase no es el esperado, lo dice en
 * el log al arrancar, con qué hacer.
 *
 * <p>No corta el arranque a propósito. Una hora mal escrita es un problema
 * serio, pero dejar una tienda sin poder abrir porque cambió una regla horaria
 * es peor; y si algún día Paraguay vuelve a mover el reloj, lo que hay que
 * cambiar es la propiedad, no el código.
 */
@Slf4j
@Component
public class ZonaHorariaGuard implements ApplicationRunner {

    private final ZoneOffset esperado;

    public ZonaHorariaGuard(
            @Value("${app.zona-horaria.desfase-esperado:-03:00}") String desfaseEsperado) {
        this.esperado = ZoneOffset.of(desfaseEsperado);
    }

    @Override
    public void run(ApplicationArguments args) {
        ZoneId zona = ZoneId.systemDefault();
        ZoneOffset actual = zona.getRules().getOffset(Instant.now());
        if (actual.equals(esperado)) {
            log.info("Zona horaria: {} ({}), coincide con la hora legal esperada", zona, actual);
            return;
        }
        log.error("""
                La hora del sistema no es la hora legal del país.
                  Zona en uso : {} ({})
                  Esperada    : {}
                Todo lo que se registre desde ahora —pedidos, facturas, cierres de caja—
                va a quedar con la hora corrida. Suele pasar porque la base de zonas del
                JDK es vieja: arrancá el backend con -Duser.timezone=Etc/GMT+3, o
                actualizá el JDK a uno con la base de zonas 2024b o posterior.""",
                zona, actual, esperado);
    }
}
