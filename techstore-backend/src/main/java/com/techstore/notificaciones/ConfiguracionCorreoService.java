package com.techstore.notificaciones;

import com.techstore.configuracion.SecretValueCipher;
import com.techstore.empresa.Configuracion;
import com.techstore.empresa.ConfiguracionRepository;
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
 */
@Service
@RequiredArgsConstructor
public class ConfiguracionCorreoService {

    private static final String HOST = "smtp.host";
    private static final String PORT = "smtp.port";
    private static final String USER = "smtp.username";
    private static final String PASS = "smtp.password";
    private static final String FROM = "smtp.from";

    private final ConfiguracionRepository repo;
    private final SecretValueCipher cipher;

    @Transactional(readOnly = true)
    public SmtpConfigResponse actual() {
        return new SmtpConfigResponse(
                valor(HOST, "smtp.gmail.com"),
                entero(PORT, 587),
                valor(USER, ""),
                valor(FROM, ""),
                !password().isBlank(),
                configurado());
    }

    @Transactional
    public void guardar(SmtpConfigRequest req) {
        set(HOST, req.host() == null || req.host().isBlank() ? "smtp.gmail.com" : req.host().trim());
        set(PORT, String.valueOf(req.port() == null ? 587 : req.port()));
        set(USER, req.username() == null ? "" : req.username().trim());
        set(FROM, req.from() == null ? "" : req.from().trim());
        // La contraseña solo se pisa si viene con valor (el form no la devuelve).
        if (req.password() != null && !req.password().isBlank()) {
            set(PASS, cipher.cifrar(req.password()));
        }
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
        sender.setHost(valor(HOST, "smtp.gmail.com"));
        sender.setPort(entero(PORT, 587));
        sender.setUsername(valor(USER, ""));
        sender.setPassword(password());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");
        return sender;
    }

    /** Remitente efectivo: el "from" si se cargó, si no el usuario SMTP. */
    @Transactional(readOnly = true)
    public String remitenteActivo() {
        String from = valor(FROM, "");
        return from.isBlank() ? valor(USER, "") : from;
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
