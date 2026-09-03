package com.techstore.reparto;

import com.techstore.envios.Envio;
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
@Table(name = "reparto_asignaciones")
@Getter
@Setter
@NoArgsConstructor
public class AsignacionReparto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "envio_id", nullable = false)
    private Envio envio;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "repartidor_id", nullable = false)
    private Usuario repartidor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asignado_por_id", nullable = false)
    private Usuario asignadoPor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private EstadoReparto estado = EstadoReparto.ASIGNADO;
    @Column(name = "orden_entrega", nullable = false)
    private Integer ordenEntrega = 1;
    @Column(columnDefinition = "text")
    private String observaciones;
    @Column(name = "fecha_asignacion", nullable = false)
    private LocalDateTime fechaAsignacion = LocalDateTime.now();
    @Column(name = "fecha_aceptacion")
    private LocalDateTime fechaAceptacion;
    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;
    @Column(name = "fecha_finalizacion")
    private LocalDateTime fechaFinalizacion;
    @Column(name = "resultado_final", columnDefinition = "text")
    private String resultadoFinal;
    @Version
    private Long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
