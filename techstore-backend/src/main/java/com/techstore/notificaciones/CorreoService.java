package com.techstore.notificaciones;

import com.techstore.comun.PageResponse;
import com.techstore.empresa.EmpresaConfigService;
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
 * siempre en la tabla {@code correos}. El envío REAL usa el SMTP configurado
 * desde la app (Admin → Correos) o, en su defecto, las credenciales por
 * variables de entorno ({@code spring.mail.username}). Si no hay ninguno, el
 * correo queda como SIMULADO. El envío nunca interrumpe la operación de negocio.
 */
@Slf4j
@Service
public class CorreoService {

    private final CorreoRepository correoRepository;
    private final ConfiguracionCorreoService configuracionCorreo;
    private final EmailTemplateService emailTemplateService;
    private final EmpresaConfigService empresaConfig;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    /** Usuario SMTP por properties/env (fallback si no se configuró desde la app). */
    private final String usuarioSmtp;

    public CorreoService(
            CorreoRepository correoRepository,
            ConfiguracionCorreoService configuracionCorreo,
            EmailTemplateService emailTemplateService,
            EmpresaConfigService empresaConfig,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.username:}") String usuarioSmtp) {
        this.correoRepository = correoRepository;
        this.configuracionCorreo = configuracionCorreo;
        this.emailTemplateService = emailTemplateService;
        this.empresaConfig = empresaConfig;
        this.mailSenderProvider = mailSenderProvider;
        this.usuarioSmtp = usuarioSmtp;
    }

    /** SMTP efectivo: primero la config de la app, luego el de properties/env. */
    private record Smtp(JavaMailSender sender, String remitente, String nombreRemitente) {
    }

    private Smtp resolverSmtp() {
        JavaMailSender sender = configuracionCorreo.senderActivo();
        if (sender != null) {
            return new Smtp(sender, configuracionCorreo.remitenteActivo(),
                    configuracionCorreo.nombreRemitenteActivo());
        }
        JavaMailSender bean = mailSenderProvider.getIfAvailable();
        if (bean != null && usuarioSmtp != null && !usuarioSmtp.isBlank()) {
            return new Smtp(bean, usuarioSmtp, null);
        }
        return null;
    }

    @Transactional
    public EstadoCorreo enviar(TipoCorreo tipo, String destinatario, String nombre,
                               String asunto, String cuerpo, String referencia) {
        return enviar(tipo, destinatario, nombre, asunto, cuerpo, referencia, null, null);
    }

    /**
     * Devuelve en qué terminó el intento.
     *
     * <p>Los avisos automáticos —la factura, la bienvenida— pueden ignorarlo:
     * que un correo no salga no tiene que voltear la venta. Pero cuando el envío
     * <em>es</em> la acción que pidió el usuario, como mandarle el pedido a un
     * proveedor, hay que poder decirle si salió, si quedó simulado por falta de
     * SMTP o si el servidor lo rechazó. Antes esto era {@code void} y quien
     * llamaba no tenía forma de saberlo.
     */
    @Transactional
    public EstadoCorreo enviar(TipoCorreo tipo, String destinatario, String nombre, String asunto,
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

        Smtp smtp = resolverSmtp();
        if (smtp != null && destinatario != null && !destinatario.isBlank()) {
            try {
                enviarMime(smtp, destinatario, asunto, cuerpo, adjunto, adjuntoNombre);
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
        return correo.getEstado();
    }

    /** Envía un correo de prueba con la config actual y devuelve el resultado. */
    @Transactional
    public PruebaCorreoResponse enviarPrueba(String destinatario) {
        Smtp smtp = resolverSmtp();
        if (smtp == null) {
            return new PruebaCorreoResponse(false,
                    "No hay SMTP configurado. Completá host, usuario y contraseña.");
        }
        String asunto = "Correo de prueba — TechStore";
        String cuerpo = emailTemplateService.construir(
                TipoCorreo.BIENVENIDA,
                "Correo de prueba",
                "administrador",
                "Este es un correo de prueba de TechStore. Si lo recibiste, el envío real de correos está funcionando. ¡Listo!",
                null, null, null);
        Correo correo = new Correo();
        correo.setTipo(TipoCorreo.BIENVENIDA);
        correo.setDestinatario(destinatario);
        correo.setAsunto(asunto);
        correo.setCuerpo(cuerpo);
        correo.setReferencia("PRUEBA");
        try {
            enviarMime(smtp, destinatario, asunto, cuerpo, null, null);
            correo.setEstado(EstadoCorreo.ENVIADO);
            correoRepository.save(correo);
            return new PruebaCorreoResponse(true, "Correo de prueba enviado a " + destinatario);
        } catch (Exception ex) {
            correo.setEstado(EstadoCorreo.ERROR);
            correo.setError(ex.getMessage());
            correoRepository.save(correo);
            return new PruebaCorreoResponse(false, "Falló el envío: " + ex.getMessage());
        }
    }

    private void enviarMime(Smtp smtp, String destinatario, String asunto, String cuerpo,
                            byte[] adjunto, String adjuntoNombre) throws Exception {
        MimeMessage mensaje = smtp.sender().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, adjunto != null, "UTF-8");
        // El remitente debe ser la casilla autenticada (Gmail y otros lo exigen);
        // el nombre visible, en cambio, es libre y lo configura la empresa.
        if (smtp.nombreRemitente() == null) {
            helper.setFrom(smtp.remitente());
        } else {
            helper.setFrom(smtp.remitente(), smtp.nombreRemitente());
        }
        // Pero las respuestas van a la casilla del negocio, que puede no ser la
        // misma. Sin esto, contestar un pedido a proveedor mandaría el mensaje a
        // la casilla técnica del SMTP en vez de a quien compra.
        String casillaEmpresa = empresaConfig.actual().email();
        if (casillaEmpresa != null && !casillaEmpresa.isBlank()) {
            helper.setReplyTo(casillaEmpresa.trim());
        }
        helper.setTo(destinatario);
        helper.setSubject(asunto);
        helper.setText(cuerpo, true);
        if (adjunto != null) {
            helper.addAttachment(adjuntoNombre, new ByteArrayResource(adjunto));
        }
        smtp.sender().send(mensaje);
    }

    @Transactional(readOnly = true)
    public PageResponse<CorreoResponse> listar(Pageable pageable) {
        return PageResponse.from(correoRepository.findAll(pageable).map(CorreoResponse::from));
    }
}
