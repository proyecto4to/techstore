package com.techstore.controller;

import com.techstore.dto.request.EmitirNotaCreditoRequest;
import com.techstore.dto.response.NotaCreditoResponse;
import com.techstore.dto.response.PageResponse;
import com.techstore.service.NotaCreditoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('Admin','Cajero')")
@Tag(name = "Notas de crédito", description = "Devoluciones de pedidos facturados")
public class NotaCreditoController {

    private final NotaCreditoService notaCreditoService;

    @PostMapping("/pedidos/{pedidoId}/nota-credito")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Emitir nota de crédito (devolución total)",
            description = "Anula la factura, devuelve el stock y registra el egreso en caja, todo en una transacción")
    public NotaCreditoResponse emitir(
            @PathVariable Long pedidoId,
            @Valid @RequestBody EmitirNotaCreditoRequest request,
            Authentication auth) {
        return notaCreditoService.emitir(pedidoId, request.motivo(), auth.getName());
    }

    @GetMapping("/notas-credito")
    @Operation(summary = "Listar notas de crédito")
    public PageResponse<NotaCreditoResponse> listar(
            @ParameterObject @PageableDefault(size = 10, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return notaCreditoService.listar(pageable);
    }

    @GetMapping(value = "/notas-credito/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Nota de crédito en PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        NotaCreditoService.NotaCreditoPdf pdf = notaCreditoService.generarPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"nota-credito-" + pdf.numero() + ".pdf\"")
                .body(pdf.contenido());
    }
}
