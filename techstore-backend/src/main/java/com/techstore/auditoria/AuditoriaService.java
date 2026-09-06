package com.techstore.auditoria;

import com.techstore.comun.Dinero;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deja rastro de las acciones sensibles: quién tocó qué, cuándo, y con qué
 * valores antes y después.
 *
 * <p>Dos decisiones que conviene tener presentes:
 *
 * <ul>
 *   <li>Auditar <b>nunca</b> puede voltear la operación auditada. Si falla el
 *       registro se anota en el log del servidor y la venta (o lo que fuera)
 *       sigue adelante: perder el rastro es malo, perder la venta es peor.
 *   <li>El registro va en su propia transacción, y por eso se usa
 *       {@link TransactionTemplate} en vez de {@code @Transactional}: al
 *       llamarse desde esta misma clase, la anotación no tendría efecto (Spring
 *       la aplica con un proxy, que la auto-invocación se saltea). Además, si el
 *       INSERT de auditoría fallara dentro de la transacción del negocio, la
 *       marcaría para rollback y tumbaría la operación igual, que es justamente
 *       lo que se quiere evitar.
 * </ul>
 */
@Service
@Slf4j
public class AuditoriaService {

    private final AuditLogRepository auditLogRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransactionTemplate transaccionPropia;

    public AuditoriaService(AuditLogRepository auditLogRepository,
                            UsuarioRepository usuarioRepository,
                            PlatformTransactionManager transactionManager) {
        this.auditLogRepository = auditLogRepository;
        this.usuarioRepository = usuarioRepository;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Acciones posibles. Se guarda el nombre tal cual (la columna admite 20 caracteres). */
    public enum Accion {
        Crear, Editar, Eliminar, Anular, Abrir, Cerrar, Cobrar, Pagar, Devolver
    }

    public void registrar(String entidad, Long entidadId, Accion accion, String detalle) {
        registrar(entidad, entidadId, accion, detalle, null);
    }

    public void registrar(String entidad, Long entidadId, Accion accion, String detalle, Cambios cambios) {
        // El usuario se resuelve fuera de la transacción nueva: el contexto de
        // seguridad es del hilo, no de la transacción.
        Optional<Usuario> autor = usuarioActual();
        try {
            transaccionPropia.executeWithoutResult(estado -> {
                AuditLog registro = new AuditLog();
                registro.setEntidad(entidad);
                registro.setEntidadId(entidadId);
                registro.setAccion(accion.name());
                registro.setDetalle(recortar(detalle));
                registro.setCambios(cambios == null || cambios.vacio() ? null : cambios.mapa());
                autor.ifPresent(usuario -> {
                    registro.setUsuarioId(usuario.getId());
                    registro.setUsuarioEmail(usuario.getEmail());
                    if (usuario.getRol() != null) {
                        registro.setUsuarioRol(usuario.getRol().getNombre());
                    }
                });
                auditLogRepository.save(registro);
            });
        } catch (Exception ex) {
            log.error("No se pudo auditar {} sobre {}#{}: {}", accion, entidad, entidadId, ex.getMessage(), ex);
        }
    }

    /**
     * El usuario autenticado, si lo hay. Puede no haberlo: una tarea del sistema
     * o el arranque de la aplicación también generan acciones auditables.
     */
    private Optional<Usuario> usuarioActual() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
                return Optional.empty();
            }
            return usuarioRepository.findByEmail(auth.getName());
        } catch (Exception ex) {
            log.warn("No se pudo resolver el usuario para auditar: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= 500 ? texto : texto.substring(0, 497) + "...";
    }

    /**
     * Acumula los campos que cambiaron. Solo entra lo que efectivamente cambió:
     * un registro lleno de campos idénticos esconde el que importa.
     */
    public static final class Cambios {

        private final Map<String, Object> mapa = new LinkedHashMap<>();

        public static Cambios nuevo() {
            return new Cambios();
        }

        public Cambios de(String campo, Object antes, Object despues) {
            if (!Objects.equals(texto(antes), texto(despues))) {
                Map<String, Object> par = new LinkedHashMap<>();
                par.put("antes", texto(antes));
                par.put("despues", texto(despues));
                mapa.put(campo, par);
            }
            return this;
        }

        /** Un valor sin "antes", para altas. */
        public Cambios valor(String campo, Object valor) {
            mapa.put(campo, texto(valor));
            return this;
        }

        public boolean vacio() {
            return mapa.isEmpty();
        }

        Map<String, Object> mapa() {
            return mapa;
        }

        /**
         * El valor tal como lo va a leer una persona en la auditoría.
         *
         * <p>Un importe pasa por {@code formateado()} y no por su
         * {@code toString()}: ese devuelve el número plano, pensado para
         * registros técnicos y para el XML del SIFEN, y en la pantalla salía
         * «Gs. 4700000» en lugar de «Gs. 4.700.000».
         */
        private static String texto(Object valor) {
            if (valor == null) {
                return null;
            }
            if (valor instanceof Dinero importe) {
                return importe.formateado();
            }
            return String.valueOf(valor);
        }
    }
}
