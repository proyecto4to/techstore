package com.techstore.caja;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Apertura de turno: efectivo con el que arranca el cajón. */
public record AbrirCajaRequest(
        @NotNull @DecimalMin(value = "0", message = "El saldo inicial no puede ser negativo")
        BigDecimal saldoInicial,
        @Size(max = 1000) String notas) {
}
