package com.techstore.controller;

import com.techstore.dto.response.ReporteVentasResponse;
import com.techstore.dto.response.StockBajoResponse;
import com.techstore.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

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
            description = "Total facturado, ventas por día, pedidos por estado, top de productos y de clientes. "
                    + "Sin fechas devuelve todo el historial; el rango es inclusivo.")
    public ReporteVentasResponse ventas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false, defaultValue = "5") @Min(1) @Max(50) int top) {
        return reporteService.ventas(desde, hasta, top);
    }

    @GetMapping("/stock-bajo")
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @Operation(summary = "Productos con stock bajo",
            description = "Productos activos cuyo stock disponible es menor o igual al umbral")
    public List<StockBajoResponse> stockBajo(
            @RequestParam(required = false, defaultValue = "5") @Min(0) @Max(1000) int umbral,
            @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) int limite) {
        return reporteService.stockBajo(umbral, limite);
    }

    @GetMapping(value = "/ventas/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @Operation(summary = "Reporte de ventas en PDF")
    public ResponseEntity<byte[]> ventasPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false, defaultValue = "5") @Min(1) @Max(50) int top) {
        return pdf("reporte-ventas.pdf", reporteService.ventasPdf(desde, hasta, top));
    }

    @GetMapping(value = "/stock-bajo/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @Operation(summary = "Reporte de stock bajo en PDF")
    public ResponseEntity<byte[]> stockBajoPdf(
            @RequestParam(required = false, defaultValue = "5") @Min(0) @Max(1000) int umbral,
            @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) int limite) {
        return pdf("stock-bajo.pdf", reporteService.stockBajoPdf(umbral, limite));
    }

    private ResponseEntity<byte[]> pdf(String nombre, byte[] contenido) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre + "\"")
                .body(contenido);
    }
}
