package com.techstore.ventas;

import com.techstore.direcciones.DireccionEntrega;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class DireccionPedidoSnapshot {

    @Column(name = "direccion_entrega_id")
    private Long id;

    @Column(name = "direccion_destinatario", length = 120)
    private String nombreDestinatario;

    @Column(name = "direccion_telefono", length = 20)
    private String telefono;

    @Column(name = "direccion_departamento", length = 80)
    private String departamento;

    @Column(name = "direccion_ciudad", length = 100)
    private String ciudad;

    @Column(name = "direccion_barrio", length = 100)
    private String barrio;

    @Column(name = "direccion_linea_1", length = 200)
    private String direccionLinea1;

    @Column(name = "direccion_linea_2", length = 200)
    private String direccionLinea2;

    @Column(name = "direccion_numero_casa", length = 40)
    private String numeroCasa;

    @Column(name = "direccion_referencia", length = 300)
    private String referencia;

    @Column(name = "direccion_codigo_postal", length = 20)
    private String codigoPostal;

    @Column(name = "direccion_latitud", precision = 10, scale = 7)
    private BigDecimal latitud;

    @Column(name = "direccion_longitud", precision = 10, scale = 7)
    private BigDecimal longitud;

    public static DireccionPedidoSnapshot desde(DireccionEntrega direccion) {
        if (direccion == null) {
            return null;
        }
        DireccionPedidoSnapshot snapshot = new DireccionPedidoSnapshot();
        snapshot.setId(direccion.getId());
        snapshot.setNombreDestinatario(direccion.getNombreDestinatario());
        snapshot.setTelefono(direccion.getTelefono());
        snapshot.setDepartamento(direccion.getDepartamento());
        snapshot.setCiudad(direccion.getCiudad());
        snapshot.setBarrio(direccion.getBarrio());
        snapshot.setDireccionLinea1(direccion.getDireccionLinea1());
        snapshot.setDireccionLinea2(direccion.getDireccionLinea2());
        snapshot.setNumeroCasa(direccion.getNumeroCasa());
        snapshot.setReferencia(direccion.getReferencia());
        snapshot.setCodigoPostal(direccion.getCodigoPostal());
        snapshot.setLatitud(direccion.getLatitud());
        snapshot.setLongitud(direccion.getLongitud());
        return snapshot;
    }
}

