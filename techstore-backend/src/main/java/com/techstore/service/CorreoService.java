package com.techstore.service;

import com.techstore.dto.response.CorreoResponse;
import com.techstore.dto.response.PageResponse;
import com.techstore.entity.Correo;
import com.techstore.entity.EstadoCorreo;
import com.techstore.entity.TipoCorreo;
import com.techstore.repository.CorreoRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificaciones por correo con bandeja de salida. Cada correo se registra
 * siempre en la tabla {@code correos}. El envío REAL por SMTP ocurre en cuanto
 * hay credenciales configuradas ({@code spring.mail.username}/{@code password});
 * si no, el correo queda como SIMULADO (modo local, sin costo ni configuración).
 * El envío nunca interrumpe la operación de negocio que lo dispara.
 */
@Slf4j
@Service
public class CorreoService {

    private final CorreoRepository correoRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    /** Usuario/casilla SMTP; si está vacío, no hay envío real (queda Simulado). */
    private final String usuarioSmtp;

    public CorreoService(
            CorreoRepository correoRepository,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.username:}") String usuarioSmtp) {
        this.correoRepository = correoRepository;
        this.mailSenderProvider = mailSenderProvider;
        this.usuarioSmtp = usuarioSmtp;
    }

    @Transactional
    public void enviar(TipoCorreo tipo, String destinatario, String nombre,
                       String asunto, String cuerpo, String referencia) {
        enviar(tipo, destinatario, nombre, asunto, cuerpo, referencia, null, null);
    }

    @Transactional
    public void enviar(TipoCorreo tipo, String destinatario, String nombre, String asunto,
                       String cuerpo, String referencia, byte[] adjunto, String adjuntoNombre) {
        Correo correo = new Correo();
        correo.setTipo(tipo);
        correo.setDestinatario(destinatario);
        correo.setDestinatarioNombre(nombre);
        correo.setAsunto(asunto);
        correo.setCuerpo(cuerpo);
        correo.setReferencia(referencia);
        correo.setTieneAdjunto(adjunto != null);
        correo.setAdjuntoNombre(adjunto != null ? adjuntoNombre : null);

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        boolean smtpConfigurado = sender != null && usuarioSmtp != null && !usuarioSmtp.isBlank();
        if (smtpConfigurado && destinatario != null && !destinatario.isBlank()) {
            try {
                MimeMessage mensaje = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mensaje, adjunto != null, "UTF-8");
                // El remitente debe ser la casilla autenticada (Gmail y otros lo exigen).
                helper.setFrom(usuarioSmtp);
                helper.setTo(destinatario);
                helper.setSubject(asunto);
                helper.setText(cuerpo, false);
                if (adjunto != null) {
                    helper.addAttachment(adjuntoNombre, new ByteArrayResource(adjunto));
                }
                sender.send(mensaje);
                correo.setEstado(EstadoCorreo.ENVIADO);
            } catch (Exception ex) {
                correo.setEstado(EstadoCorreo.ERROR);
                correo.setError(ex.getMessage());
                log.warn("No se pudo enviar el correo '{}' a {}: {}", asunto, destinatario, ex.getMessage());
            }
        } else {
            correo.setEstado(EstadoCorreo.SIMULADO);
        }

        correoRepository.save(correo);
    }

    @Transactional(readOnly = true)
    public PageResponse<CorreoResponse> listar(Pageable pageable) {
        return PageResponse.from(correoRepository.findAll(pageable).map(CorreoResponse::from));
    }
}
