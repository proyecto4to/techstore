package com.techstore.reparto;

public enum EstadoReparto {
    ASIGNADO,
    ACEPTADO,
    EN_RUTA,
    EN_DESTINO,
    ENTREGADO,
    NO_ENTREGADO,
    CANCELADO;

    public boolean terminal() {
        return this == ENTREGADO || this == NO_ENTREGADO || this == CANCELADO;
    }
}
