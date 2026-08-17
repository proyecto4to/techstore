package com.techstore.mensajeria;

import com.techstore.usuarios.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;

@Entity
@Table(name = "mensajes")
@Getter
@Setter
@NoArgsConstructor
public class Mensaje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "conversacion_id", nullable = false)
    private Conversacion conversacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "remitente_id", nullable = false)
    private Usuario remitente;
    @Column(name = "client_message_id", nullable = false, length = 100)
    private String clientMessageId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TipoMensaje tipo;
    @Column(nullable = false, columnDefinition = "text")
    private String contenido;
    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn = LocalDateTime.now();
    @Column(name = "entregado_en")
    private LocalDateTime entregadoEn;
    @Column(name = "leido_en")
    private LocalDateTime leidoEn;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
