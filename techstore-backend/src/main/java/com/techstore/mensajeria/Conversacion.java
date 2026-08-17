package com.techstore.mensajeria;

import com.techstore.usuarios.Usuario;
import com.techstore.ventas.Pedido;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversaciones")
@Getter
@Setter
@NoArgsConstructor
public class Conversacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pedido_id")
    private Pedido pedido;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EstadoConversacion estado = EstadoConversacion.ABIERTA;
    @Column(name = "ultimo_mensaje_en")
    private LocalDateTime ultimoMensajeEn;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
