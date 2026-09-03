package com.techstore.reparto;

import com.techstore.usuarios.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;

@Entity
@Table(name = "reparto_historial")
@Getter
@Setter
@NoArgsConstructor
public class RepartoHistorial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asignacion_id", nullable = false)
    private AsignacionReparto asignacion;
    @Enumerated(EnumType.STRING) @Column(name = "estado_anterior", length = 30)
    private EstadoReparto estadoAnterior;
    @Enumerated(EnumType.STRING) @Column(name = "estado_nuevo", nullable = false, length = 30)
    private EstadoReparto estadoNuevo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
    @Column(columnDefinition = "text")
    private String observacion;
}
