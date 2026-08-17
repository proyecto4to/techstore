package com.techstore.contabilidad;

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

/**
 * Libros de IVA y presentación a la DNIT.
 *
 * <p>Solo Admin: acá se ve el margen del negocio entero y sale el archivo que
 * se le presenta a la administración tributaria. Un cajero no tiene por qué
 * llegar a esto.
 */
@RestController
@RequestMapping("/api/v1/contabilidad")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('Admin')")
@Tag(name = "Contabilidad", description = "Libros de IVA y Registro de Comprobantes de la DNIT")
public class ContabilidadController {

    private final LibroIvaService libroIvaService;
    private final LibroPdfService libroPdfService;
    private final RegistroComprobantesService registroService;

    @GetMapping("/libro-ventas")
    @Operation(summary = "Libro de IVA — Ventas",
            description = "Facturas y notas de crédito del período, con el desglose por tasa. "
                    + "Las anuladas quedan afuera. Con desde = hasta funciona como cierre diario.")
    public LibroIvaResponse libroVentas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return libroIvaService.ventas(desde, hasta);
    }

    @GetMapping("/libro-compras")
    @Operation(summary = "Libro de IVA — Compras",
            description = "Los comprobantes que emitieron los proveedores, por su fecha de emisión")
    public LibroIvaResponse libroCompras(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return libroIvaService.compras(desde, hasta);
    }

    @GetMapping("/resumen-iva")
    @Operation(summary = "Liquidación del IVA del período",
            description = "Débito fiscal de las ventas contra crédito fiscal de las compras")
    public ResumenIvaResponse resumenIva(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return libroIvaService.resumen(desde, hasta);
    }

    @GetMapping(value = "/libro-ventas/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Libro de IVA — Ventas, en PDF")
    public ResponseEntity<byte[]> libroVentasPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return pdf("libro-iva-ventas.pdf", libroPdfService.libro(libroIvaService.ventas(desde, hasta)));
    }

    @GetMapping(value = "/libro-compras/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Libro de IVA — Compras, en PDF")
    public ResponseEntity<byte[]> libroComprasPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return pdf("libro-iva-compras.pdf", libroPdfService.libro(libroIvaService.compras(desde, hasta)));
    }

    @GetMapping(value = "/resumen-iva/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Liquidación del IVA en PDF")
    public ResponseEntity<byte[]> resumenIvaPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return pdf("liquidacion-iva.pdf", libroPdfService.resumen(libroIvaService.resumen(desde, hasta)));
    }

    @GetMapping(value = "/registro-comprobantes", produces = "application/zip")
    @Operation(summary = "Archivo del Registro de Comprobantes para Marangatu",
            description = "El ZIP que se sube a Marangatu (RG 90/2021). Con mes es el registro mensual "
                    + "(obligación 955); sin mes, el anual (obligación 956). No se genera si al período "
                    + "le falta algún dato obligatorio: Marangatu rechaza el archivo entero, no la fila.")
    public ResponseEntity<byte[]> registroComprobantes(
            @RequestParam @Min(2000) @Max(2100) int anio,
            @RequestParam(required = false) @Min(1) @Max(12) Integer mes,
            @RequestParam(defaultValue = "TXT") FormatoArchivo formato,
            @RequestParam(defaultValue = "V0001") String identificador,
            @RequestParam(defaultValue = "IRE") ImpuestoALaRenta renta) {
        RegistroComprobantesService.Archivo archivo =
                registroService.generar(anio, mes, formato, identificador, renta);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + archivo.nombre() + "\"")
                .body(archivo.contenido());
    }

    private ResponseEntity<byte[]> pdf(String nombre, byte[] contenido) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre + "\"")
                .body(contenido);
    }
}
