package com.techstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Intentos fallidos de login para un email. La clave es el propio email en
 * minúsculas: no hay id autogenerado porque hay a lo sumo una fila por email y
 * así el upsert es directo.
 *
 * <p>Se guarda aunque el email no exista como usuario: si solo se contaran los
 * emails registrados, probar cuáles se bloquean sería una forma de averiguar
 * qué cuentas existen.
 */
@Entity
@Table(name = "intento_login")
@Getter
@Setter
@NoArgsConstructor
public class IntentoLogin {

    @Id
    @Column(length = 255)
    private String email;

    @Column(nullable = false)
    private Integer fallos = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "ultimo_intento", nullable = false)
    private LocalDateTime ultimoIntento = LocalDateTime.now();
}
