package com.techstore.envios;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;

@Entity
@Table(name = "envio_eventos")
@Getter
@Setter
@NoArgsConstructor
public class EnvioEvento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "envio_id", nullable = false)
    private Envio envio;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private EstadoEnvio estado;
    @Column(nullable = false, length = 300)
    private String descripcion;
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();
    @Column(length = 200)
    private String ubicacion;
}
