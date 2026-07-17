package com.techstore.controller;

import com.techstore.dto.response.ReporteVentasResponse;
import com.techstore.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reportes", description = "Indicadores de venta para Admin y Cajero")
public class ReporteController {

    private final ReporteService reporteService;

    @GetMapping("/ventas")
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @Operation(summary = "Reporte de ventas",
            description = "Total facturado, pedidos por estado y productos más vendidos")
    public ReporteVentasResponse ventas(
            @RequestParam(required = false, defaultValue = "5") @Min(1) @Max(50) int top) {
        return reporteService.ventas(top);
    }
}
