package com.techstore.security;

import com.techstore.entity.IntentoLogin;
import com.techstore.exception.TooManyAttemptsException;
import com.techstore.repository.IntentoLoginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Bloqueo contra fuerza bruta en el login: tras varios intentos fallidos
 * seguidos para un email, lo bloquea un rato.
 *
 * <p>El estado vive en la base y no en memoria del proceso. Cuando vivía en
 * memoria, cada reinicio del backend borraba los bloqueos, así que bastaba con
 * esperar (o provocar) un reinicio para volver a tener los intentos limpios.
 *
 * <p>Los contadores se escriben en su propia transacción
 * ({@code REQUIRES_NEW}): el login falla con excepción, y si el contador
 * compartiera la transacción del intento, el rollback se llevaría puesto el
 * registro del fallo — que es justamente lo que hay que conservar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginRateLimiter {

    private static final int MAX_INTENTOS = 5;
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);
    /** Después de este tiempo sin actividad, la fila no aporta nada y se borra. */
    private static final Duration RETENCION = Duration.ofDays(7);

    private final IntentoLoginRepository repo;

    /** Lanza {@link TooManyAttemptsException} si el email está bloqueado por demasiados fallos. */
    @Transactional(readOnly = true)
    public void verificarDisponible(String email) {
        repo.findById(normalizar(email)).ifPresent(intento -> {
            LocalDateTime bloqueo = intento.getBloqueadoHasta();
            if (bloqueo == null) {
                return;
            }
            LocalDateTime ahora = LocalDateTime.now();
            if (ahora.isBefore(bloqueo)) {
                long minutos = Math.max(1, Duration.between(ahora, bloqueo).toMinutes() + 1);
                throw new TooManyAttemptsException(
                        "Demasiados intentos fallidos. Probá de nuevo en " + minutos + " minuto(s).");
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFallo(String email) {
        String clave = normalizar(email);
        LocalDateTime ahora = LocalDateTime.now();

        IntentoLogin intento = repo.findById(clave).orElseGet(() -> {
            IntentoLogin nuevo = new IntentoLogin();
            nuevo.setEmail(clave);
            return nuevo;
        });

        // Si el bloqueo anterior ya venció, el conteo arranca de cero: cinco
        // fallos de hoy no se suman a los de la semana pasada.
        if (intento.getBloqueadoHasta() != null && ahora.isAfter(intento.getBloqueadoHasta())) {
            intento.setFallos(0);
            intento.setBloqueadoHasta(null);
        }

        intento.setFallos(intento.getFallos() + 1);
        intento.setUltimoIntento(ahora);
        if (intento.getFallos() >= MAX_INTENTOS) {
            intento.setBloqueadoHasta(ahora.plus(DURACION_BLOQUEO));
        }
        repo.save(intento);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarExito(String email) {
        repo.deleteById(normalizar(email));
    }

    /** Borra los registros que ya no bloquean a nadie. Una vez por día alcanza. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void limpiar() {
        int borrados = repo.borrarAnterioresA(LocalDateTime.now().minus(RETENCION));
        if (borrados > 0) {
            log.info("Intentos de login viejos borrados: {}", borrados);
        }
    }

    private static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
