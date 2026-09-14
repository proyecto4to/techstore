package com.techstore.seguridad;

import com.techstore.usuarios.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * La excepción de una persona sobre la matriz de su rol.
 *
 * <p>Existe para no tener que inventar un rol nuevo cada vez que a alguien le
 * falta —o le sobra— una acción suelta. Un cajero que además exporta reportes
 * no es un rol distinto: es ese cajero.
 *
 * <p>No tener fila es lo normal y significa «lo que diga el rol». Guardar
 * {@code permitido = false} niega aunque el rol permita, y es lo único que
 * puede hacerlo. Guardar {@code true} concede, pero nunca por encima de las
 * anotaciones del backend, que se comprueban después y siguen siendo el piso.
 */
@Entity
@Table(name = "permisos_usuario")
@Getter
@Setter
@NoArgsConstructor
public class PermisoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Modulo modulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccionPermiso accion;

    @Column(nullable = false)
    private Boolean permitido;

    /** Por qué se hizo la excepción, para que dentro de un año se entienda. */
    @Column(length = 200)
    private String motivo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
