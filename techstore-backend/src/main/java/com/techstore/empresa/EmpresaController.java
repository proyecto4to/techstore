package com.techstore.empresa;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/empresa")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Admin')")
@Tag(name = "Empresa", description = "Datos del emisor que salen en la factura y en el Documento Electrónico")
public class EmpresaController {

    private final EmpresaConfigService empresaConfigService;

    @GetMapping
    @Operation(summary = "Datos actuales del emisor")
    public EmpresaProperties actual() {
        return empresaConfigService.actual();
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Qué datos del emisor faltan para poder facturar",
            description = "El panel lo consulta para avisar antes de vender, en vez de que el aviso "
                    + "llegue como un error en medio de un cobro.")
    public DatosEmisorPendientesResponse pendientes() {
        return empresaConfigService.pendientes();
    }

    @PutMapping
    @Operation(summary = "Editar los datos del emisor",
            description = "Los campos que se manden vacíos vuelven al valor de application.properties. "
                    + "Afecta a las facturas que se emitan de acá en adelante.")
    public EmpresaProperties guardar(@Valid @RequestBody EmpresaRequest request) {
        return empresaConfigService.guardar(request);
    }
}
