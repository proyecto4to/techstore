package com.techstore.ventas;

import com.techstore.usuarios.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;

/** Una imagen que respalda el pago de un pedido. */
@Entity
@Table(name = "pago_comprobantes")
@Getter
@Setter
@NoArgsConstructor
public class PagoComprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    /**
     * El nombre con el que quedó guardado, con UUID.
     *
     * <p>Nunca el que traía el dispositivo: un nombre de archivo que llega de
     * afuera puede contener separadores de ruta y terminar escribiendo donde no
     * corresponde.
     */
    @Column(name = "archivo_nombre", nullable = false, length = 255)
    private String archivoNombre;

    @Column(name = "archivo_content_type", nullable = false, length = 100)
    private String archivoContentType;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subido_por")
    private Usuario subidoPor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
