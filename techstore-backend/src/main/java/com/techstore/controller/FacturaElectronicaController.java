package com.techstore.controller;

import com.techstore.sifen.FacturaElectronicaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/facturas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('Admin','Cajero')")
@Tag(name = "Factura electrónica (SIFEN)",
        description = "Documento Electrónico firmado y KuDE, demostrativo del estándar SIFEN v150")
public class FacturaElectronicaController {

    private final FacturaElectronicaService facturaElectronicaService;

    @GetMapping(value = "/{id}/documento-electronico", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "XML del Documento Electrónico firmado")
    public ResponseEntity<byte[]> xml(@PathVariable Long id) {
        FacturaElectronicaService.XmlFirmado de = facturaElectronicaService.generarXml(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"DE-" + de.cdc() + ".xml\"")
                .body(de.xml().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/{id}/kude", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "KuDE (representación gráfica con QR) en PDF")
    public ResponseEntity<byte[]> kude(@PathVariable Long id) {
        FacturaElectronicaService.Kude kude = facturaElectronicaService.generarKude(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"KuDE-" + kude.numero() + ".pdf\"")
                .body(kude.pdf());
    }
}
