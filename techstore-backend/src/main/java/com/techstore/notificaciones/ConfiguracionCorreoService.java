package com.techstore.notificaciones;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.configuracion.SecretValueCipher;
import com.techstore.empresa.Configuracion;
import com.techstore.empresa.ConfiguracionRepository;
import com.techstore.usuarios.ReautenticacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Properties;

/**
 * Configuración del SMTP editable desde la app (Admin → Correos). Guarda las
 * credenciales en la tabla {@code configuracion} y arma un {@link JavaMailSender}
 * al vuelo con ellas, para poder activar el envío real sin tocar archivos ni
 * reiniciar.
 *
 * <p>Editarla exige volver a escribir la contraseña del administrador. No es
 * una molestia de más: quien cambie el host y el usuario del correo saliente se
 * queda con la recuperación de contraseñas de todas las cuentas, así que alcanza
 * con encontrar una sesión abierta y sin dueño delante.
 *
 * <p>Esa comprobación viene de {@code ReautenticacionService} y no de
 * {@code AuthService}: este servicio es dependencia del de correo, y el de
 * correo lo es de {@code AuthService}, así que pedírselo a él cierra un ciclo
 * que impide levantar la aplicación.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracionCorreoService {

    private static final String HOST = "smtp.host";
    private static final String PORT = "smtp.port";
    private static final String USER = "smtp.username";
    private static final String PASS = "smtp.password";
    private static final String FROM = "smtp.from";
    private static final String FROM_NOMBRE = "smtp.from-nombre";
    private static final String SEGURIDAD = "smtp.seguridad";

    private static final String HOST_POR_DEFECTO = "smtp.gmail.com";
    private static final int PUERTO_POR_DEFECTO = 587;

    private final ConfiguracionRepository repo;
    private final SecretValueCipher cipher;
    private final ReautenticacionService reautenticacionService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public SmtpConfigResponse actual() {
        return new SmtpConfigResponse(
                valor(HOST, HOST_POR_DEFECTO),
                entero(PORT, PUERTO_POR_DEFECTO),
                valor(USER, ""),
                valor(FROM, ""),
                valor(FROM_NOMBRE, ""),
                seguridad().name(),
                !password().isBlank(),
                configurado());
    }

    /**
     * Guarda la configuración, después de comprobar que quien la edita es quien
     * dice ser.
     *
     * <p>La reautenticación va primero: si la contraseña no coincide no se toca
     * nada. Y lo que se audita es el cambio de servidor, usuario y remitente
     * —nunca la contraseña ni su longitud—, que es lo que permite reconstruir
     * quién desvió el correo si algún día hace falta.
     */
    @Transactional
    public SmtpConfigResponse guardar(String email, SmtpConfigRequest req) {
        reautenticacionService.verificar(email, req.passwordActual());

        SmtpConfigResponse anterior = actual();
        SeguridadSmtp seguridad = SeguridadSmtp.desde(req.seguridad(), SeguridadSmtp.STARTTLS);

        set(HOST, req.host() == null || req.host().isBlank() ? HOST_POR_DEFECTO : req.host().trim());
        set(PORT, String.valueOf(req.port() == null ? PUERTO_POR_DEFECTO : req.port()));
        set(USER, req.username() == null ? "" : req.username().trim());
        set(FROM, req.from() == null ? "" : req.from().trim());
        set(FROM_NOMBRE, req.fromNombre() == null ? "" : req.fromNombre().trim());
        set(SEGURIDAD, seguridad.name());
        // La contraseña solo se pisa si viene con valor: la pantalla no la
        // muestra, así que no puede devolverla al guardar el resto.
        if (req.password() != null && !req.password().isBlank()) {
            set(PASS, cipher.cifrar(req.password()));
        }

        SmtpConfigResponse nueva = actual();
        auditoriaService.registrar("ConfiguracionSmtp", null, AuditoriaService.Accion.Editar,
                "Cambió la configuración del correo saliente",
                AuditoriaService.Cambios.nuevo()
                        .de("host", anterior.host(), nueva.host())
                        .de("puerto", String.valueOf(anterior.port()), String.valueOf(nueva.port()))
                        .de("usuario", anterior.username(), nueva.username())
                        .de("remitente", anterior.from(), nueva.from())
                        .de("seguridad", anterior.seguridad(), nueva.seguridad())
                        .de("contraseña",
                                anterior.tienePassword() ? "definida" : "sin definir",
                                nueva.tienePassword() ? "definida" : "sin definir"));
        return nueva;
    }

    @Transactional(readOnly = true)
    public boolean configurado() {
        return !valor(HOST, "").isBlank() && !valor(USER, "").isBlank() && !password().isBlank();
    }

    /** {@link JavaMailSender} armado con la config guardada, o {@code null} si no está completa. */
    @Transactional(readOnly = true)
    public JavaMailSender senderActivo() {
        if (!configurado()) {
            return null;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(valor(HOST, HOST_POR_DEFECTO));
        sender.setPort(entero(PORT, PUERTO_POR_DEFECTO));
        sender.setUsername(valor(USER, ""));
        sender.setPassword(password());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");
        aplicarSeguridad(props, seguridad());
        return sender;
    }

    /**
     * Traduce la opción elegida a las propiedades de JavaMail.
     *
     * <p>Con STARTTLS se exige que el servidor lo soporte —{@code required}— en
     * lugar de solo intentarlo: sin eso, un servidor que no lo ofrezca hace que
     * la sesión siga en texto plano sin avisar, que es justo lo que se quería
     * evitar al elegir la opción.
     */
    private static void aplicarSeguridad(Properties props, SeguridadSmtp seguridad) {
        switch (seguridad) {
            case STARTTLS -> {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case SSL -> {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case NINGUNA -> {
                props.put("mail.smtp.starttls.enable", "false");
                props.put("mail.smtp.ssl.enable", "false");
            }
        }
    }

    /** Remitente efectivo: el "from" si se cargó, si no el usuario SMTP. */
    @Transactional(readOnly = true)
    public String remitenteActivo() {
        String from = valor(FROM, "");
        return from.isBlank() ? valor(USER, "") : from;
    }

    /** Cómo firmar el remitente, cuando se le puso un nombre. */
    @Transactional(readOnly = true)
    public String nombreRemitenteActivo() {
        String nombre = valor(FROM_NOMBRE, "");
        return nombre.isBlank() ? null : nombre;
    }

    private SeguridadSmtp seguridad() {
        return SeguridadSmtp.desde(valor(SEGURIDAD, null), SeguridadSmtp.STARTTLS);
    }

    private String valor(String clave, String porDefecto) {
        return repo.findById(clave).map(Configuracion::getValor)
                .filter(v -> v != null)
                .orElse(porDefecto);
    }

    private String password() {
        return cipher.descifrar(valor(PASS, ""));
    }

    private int entero(String clave, int porDefecto) {
        try {
            return Integer.parseInt(valor(clave, String.valueOf(porDefecto)).trim());
        } catch (NumberFormatException ex) {
            return porDefecto;
        }
    }

    private void set(String clave, String valor) {
        Configuracion c = repo.findById(clave).orElseGet(() -> new Configuracion(clave, null));
        c.setValor(valor);
        repo.save(c);
    }
}
