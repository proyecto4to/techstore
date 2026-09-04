package com.techstore.caja;

import com.techstore.comun.PageResponse;
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
@Tag(name = "Caja", description = "Movimientos de efectivo: ingresos, egresos, arqueo y cierre")
public class CajaController {

    private final CajaService cajaService;
    private final CierreCajaService cierreCajaService;

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
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Arqueo de caja", description = "Ingresos, egresos y saldo del período. Solo administracion: el neto de efectivo es el monto esperado del turno")
    public ResumenCajaResponse resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return cajaService.resumen(desde, hasta);
    }

    @GetMapping(value = "/resumen/pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Arqueo de caja en PDF", description = "Resumen y detalle de movimientos del período. Solo administracion")
    public org.springframework.http.ResponseEntity<byte[]> resumenPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"arqueo-caja.pdf\"")
                .body(cajaService.resumenPdf(desde, hasta));
    }

    @GetMapping("/apertura")
    @Operation(summary = "Turno de caja abierto",
            description = "Devuelve la apertura en curso, o abierta=false si la caja está cerrada")
    public AperturaCajaResponse aperturaActual() {
        return cierreCajaService.aperturaActual();
    }

    @PostMapping("/apertura")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abrir caja",
            description = "Inicia un turno declarando el efectivo con el que arranca el cajón")
    public AperturaCajaResponse abrir(@Valid @RequestBody AbrirCajaRequest request, Authentication auth) {
        return cierreCajaService.abrir(request, auth.getName());
    }

    @GetMapping("/cierres/preview")
    @Operation(summary = "Vista previa del cierre",
            description = "Efectivo teórico del turno abierto (desde el último cierre) antes de contar el físico")
    public PreviewCierreResponse previewCierre(Authentication auth) {
        PreviewCierreResponse preview = cierreCajaService.preview();
        return esAdmin(auth) ? preview : preview.sinArqueo();
    }

    @PostMapping("/cierres")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cerrar caja",
            description = "Registra el arqueo del turno con el efectivo contado y la diferencia respecto al teórico")
    public CierreCajaResponse cerrar(@Valid @RequestBody CerrarCajaRequest request, Authentication auth) {
        CierreCajaResponse cierre = cierreCajaService.cerrar(request, auth.getName());
        return esAdmin(auth) ? cierre : cierre.sinArqueo();
    }

    @GetMapping("/cierres")
    @Operation(summary = "Listar cierres de caja")
    public PageResponse<CierreCajaResponse> listarCierres(
            @ParameterObject @PageableDefault(size = 10, sort = "fechaCierre", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        PageResponse<CierreCajaResponse> cierres = cierreCajaService.listar(pageable);
        if (esAdmin(auth)) {
            return cierres;
        }
        return cierres.map(CierreCajaResponse::sinArqueo);
    }

    /**
     * El arqueo -efectivo teorico, diferencia, sobrante o faltante- es
     * informacion de control sobre el propio cajero: si la ve mientras cuenta,
     * deja de ser una declaracion y pasa a ser un numero para cuadrar. Por eso
     * se filtra en el servidor y no solo en la pantalla: ocultar un boton no
     * impide llamar al endpoint.
     */
    private boolean esAdmin(Authentication auth) {
        return auth != null
                && auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_Admin".equals(a.getAuthority()));
    }
}
