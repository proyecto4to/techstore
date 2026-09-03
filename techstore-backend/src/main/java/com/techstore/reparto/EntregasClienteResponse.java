package com.techstore.reparto;

import java.util.List;

public record EntregasClienteResponse(
        Long clienteId,
        String cliente,
        String direccion,
        List<RepartoResponse> entregas) {
}
