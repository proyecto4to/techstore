package com.techstore.notificaciones;

import com.techstore.comun.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/correos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('Admin','Cajero')")
@Tag(name = "Correos", description = "Bandeja de salida y configuración de envío de correos")
public class CorreoController {

    private final CorreoService correoService;
    private final ConfiguracionCorreoService configuracionCorreoService;

    @GetMapping
    @Operation(summary = "Listar correos enviados/registrados (bandeja de salida)")
    public PageResponse<CorreoResponse> listar(
            @ParameterObject @PageableDefault(size = 15, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return correoService.listar(pageable);
    }

    @GetMapping("/config")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Configuración SMTP actual (sin la contraseña)")
    public SmtpConfigResponse config() {
        return configuracionCorreoService.actual();
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Guardar la configuración SMTP para el envío real de correos")
    public SmtpConfigResponse guardarConfig(@RequestBody SmtpConfigRequest request) {
        configuracionCorreoService.guardar(request);
        return configuracionCorreoService.actual();
    }

    @PostMapping("/prueba")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Enviar un correo de prueba con la configuración actual")
    public PruebaCorreoResponse prueba(@Valid @RequestBody PruebaCorreoRequest request) {
        return correoService.enviarPrueba(request.destinatario());
    }
}
