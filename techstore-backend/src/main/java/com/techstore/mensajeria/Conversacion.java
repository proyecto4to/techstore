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
    /** Quien la abrió. En las de soporte es el cliente. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "iniciador_id", nullable = false)
    private Usuario iniciador;
    /** Con quién habla. Nulo en soporte, porque el otro extremo es la tienda. */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "destinatario_id")
    private Usuario destinatario;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TipoConversacion tipo = TipoConversacion.SOPORTE;
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

    /** El otro extremo visto desde {@code usuario}, o nulo si es la tienda. */
    public Usuario contraparte(Usuario usuario) {
        if (tipo == TipoConversacion.SOPORTE) {
            return usuario.getId().equals(iniciador.getId()) ? null : iniciador;
        }
        return usuario.getId().equals(iniciador.getId()) ? destinatario : iniciador;
    }

    public boolean participa(Usuario usuario) {
        Long id = usuario.getId();
        return id.equals(iniciador.getId())
                || (destinatario != null && id.equals(destinatario.getId()));
    }
}
