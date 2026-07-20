package com.techstore.controller;

import com.techstore.dto.request.RegistroMovimientoRequest;
import com.techstore.dto.response.MovimientoCajaResponse;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.ResumenCajaResponse;
import com.techstore.service.CajaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/caja")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('Admin','Cajero')")
@Tag(name = "Caja", description = "Movimientos de efectivo: ingresos, egresos y arqueo")
public class CajaController {

    private final CajaService cajaService;

    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar movimiento manual (Admin/Cajero)")
    public MovimientoCajaResponse registrar(
            @Valid @RequestBody RegistroMovimientoRequest request, Authentication auth) {
        return cajaService.registrar(request, auth.getName());
    }

    @GetMapping("/movimientos")
    @Operation(summary = "Listar movimientos de caja",
            description = "Paginado, con rango de fechas inclusivo opcional")
    public PageResponse<MovimientoCajaResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(size = 10, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return cajaService.listar(desde, hasta, pageable);
    }

    @GetMapping("/resumen")
    @Operation(summary = "Arqueo de caja", description = "Ingresos, egresos y saldo del período")
    public ResumenCajaResponse resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return cajaService.resumen(desde, hasta);
    }

    @GetMapping(value = "/resumen/pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Arqueo de caja en PDF", description = "Resumen y detalle de movimientos del período")
    public org.springframework.http.ResponseEntity<byte[]> resumenPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"arqueo-caja.pdf\"")
                .body(cajaService.resumenPdf(desde, hasta));
    }
}
