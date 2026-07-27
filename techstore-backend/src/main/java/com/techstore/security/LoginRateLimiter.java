package com.techstore.security;

import com.techstore.exception.TooManyAttemptsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bloqueo simple en memoria contra fuerza bruta en el login: tras varios
 * intentos fallidos seguidos para un email, lo bloquea un rato. Al vivir en
 * memoria del proceso, se reinicia si el backend se reinicia, lo cual es
 * aceptable para un sistema que corre en una sola instancia.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_INTENTOS = 5;
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private static final class Estado {
        final AtomicInteger fallos = new AtomicInteger(0);
        volatile Instant bloqueadoHasta;
    }

    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();

    /** Lanza {@link TooManyAttemptsException} si el email está bloqueado por demasiados fallos. */
    public void verificarDisponible(String email) {
        String clave = normalizar(email);
        Estado estado = estados.get(clave);
        if (estado == null || estado.bloqueadoHasta == null) {
            return;
        }
        Instant ahora = Instant.now();
        if (ahora.isBefore(estado.bloqueadoHasta)) {
            long minutos = Math.max(1, Duration.between(ahora, estado.bloqueadoHasta).toMinutes() + 1);
            throw new TooManyAttemptsException(
                    "Demasiados intentos fallidos. Probá de nuevo en " + minutos + " minuto(s).");
        }
        // El bloqueo ya expiró: se descarta para que el próximo fallo empiece a contar de cero.
        estados.remove(clave);
    }

    public void registrarFallo(String email) {
        Estado estado = estados.computeIfAbsent(normalizar(email), k -> new Estado());
        if (estado.fallos.incrementAndGet() >= MAX_INTENTOS) {
            estado.bloqueadoHasta = Instant.now().plus(DURACION_BLOQUEO);
        }
    }

    public void registrarExito(String email) {
        estados.remove(normalizar(email));
    }

    private static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
