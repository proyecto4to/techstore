package com.techstore.ventas;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * El cobro por transferencia: a dónde transferir, el comprobante que se sube
 * antes de confirmar la compra y la bandeja de pagos por verificar.
 */
@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Transferencia bancaria: datos del comercio, comprobantes y verificación")
public class CobroTransferenciaController {

    private final DatosTransferenciaService datosTransferencia;
    private final PagoComprobanteService comprobanteService;
    private final VerificacionPagoService verificacionService;

    @GetMapping("/transferencia")
    @Operation(summary = "A dónde transferir",
            description = "disponible=false mientras la tienda no cargó banco, titular y una cuenta o un alias")
    public DatosTransferenciaResponse datosTransferencia() {
        return datosTransferencia.actuales();
    }

    @GetMapping("/transferencia/qr")
    @Operation(summary = "El QR que la tienda sacó de la app de su banco")
    public ResponseEntity<byte[]> qr() {
        var qr = datosTransferencia.qr();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, qr.contentType())
                .cacheControl(CacheControl.noStore())
                .body(qr.contenido());
    }

    @PutMapping("/transferencia")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Guardar los datos para cobrar por transferencia",
            description = "Un campo vacío lo borra. El cambio queda en la auditoría con el antes y el después")
    public DatosTransferenciaResponse guardarDatosTransferencia(@Valid @RequestBody DatosTransferenciaRequest request) {
        return datosTransferencia.guardar(request);
    }

    @PostMapping(value = "/transferencia/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Subir el QR del banco", description = "PNG o JPG de hasta 2 MB")
    public DatosTransferenciaResponse subirQr(@RequestPart("archivo") MultipartFile archivo) {
        return datosTransferencia.guardarQr(archivo);
    }

    @DeleteMapping("/transferencia/qr")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Quitar el QR del banco")
    public DatosTransferenciaResponse quitarQr() {
        return datosTransferencia.quitarQr();
    }

    @PostMapping(value = "/comprobantes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subir un comprobante antes de confirmar la compra",
            description = "Queda solo de quien lo subió hasta que un checkout lo use con comprobanteIds. "
                    + "Si en un día no se usa, se borra solo")
    public PagoComprobanteResponse subirComprobante(
            @RequestPart("archivo") MultipartFile archivo, Authentication auth) {
        return comprobanteService.subirSuelto(archivo, auth.getName());
    }

    @DeleteMapping("/comprobantes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar un comprobante que todavía no se usó")
    public void quitarComprobante(@PathVariable Long id, Authentication auth) {
        comprobanteService.quitarSuelto(id, auth.getName());
    }

    @GetMapping("/comprobantes/{id}/archivo")
    @Operation(summary = "Ver un comprobante que todavía no se usó")
    public ResponseEntity<byte[]> archivoComprobante(@PathVariable Long id, Authentication auth) {
        var archivo = comprobanteService.archivoSuelto(id, auth.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, archivo.contentType())
                .cacheControl(CacheControl.noStore())
                .body(archivo.contenido());
    }

    @GetMapping("/verificaciones")
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @Operation(summary = "Pagos por verificar",
            description = "Con todos=true suma los que esperan un comprobante del cliente")
    public List<PagoPendienteResponse> verificaciones(
            @RequestParam(name = "todos", defaultValue = "false") boolean todos) {
        return verificacionService.pendientes(todos);
    }

    @GetMapping("/verificaciones/cuantos")
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @Operation(summary = "Cuántos pagos esperan revisión")
    public Map<String, Long> cuantosParaVerificar() {
        return Map.of("enVerificacion", verificacionService.cuantosParaRevisar());
    }
}
