package com.techstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Registro de un correo de notificación (bandeja de salida). Persiste siempre,
 * sin importar si el envío real por SMTP está habilitado.
 */
@Entity
@Table(name = "correos")
@Getter
@Setter
@NoArgsConstructor
public class Correo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoCorreo tipo;

    @Column(nullable = false, length = 150)
    private String destinatario;

    @Column(name = "destinatario_nombre", length = 200)
    private String destinatarioNombre;

    @Column(nullable = false, length = 255)
    private String asunto;

    @Column(nullable = false, columnDefinition = "text")
    private String cuerpo;

    @Column(length = 50)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCorreo estado = EstadoCorreo.PENDIENTE;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "tiene_adjunto", nullable = false)
    private boolean tieneAdjunto;

    @Column(name = "adjunto_nombre", length = 150)
    private String adjuntoNombre;

    @CreationTimestamp
    @Column(name = "fecha", updatable = false)
    private LocalDateTime fecha;
}
