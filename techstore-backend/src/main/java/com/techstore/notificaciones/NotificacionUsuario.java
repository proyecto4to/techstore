package com.techstore.notificaciones;

import com.techstore.notificaciones.outbox.NotificationEventType;
import com.techstore.usuarios.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones_usuario")
@Getter
@Setter
@NoArgsConstructor
public class NotificacionUsuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private NotificationEventType tipo;
    @Column(nullable = false, length = 160)
    private String titulo;
    @Column(nullable = false, length = 500)
    private String mensaje;
    @Column(name = "referencia_tipo", nullable = false, length = 40)
    private String referenciaTipo;
    @Column(name = "referencia_id", nullable = false)
    private Long referenciaId;
    @Column(name = "deep_link", nullable = false, length = 255)
    private String deepLink;
    @Column(nullable = false)
    private boolean leida;
    @Column(name = "leida_en")
    private LocalDateTime leidaEn;
    @Column(name = "source_outbox_id", nullable = false)
    private Long sourceOutboxId;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
