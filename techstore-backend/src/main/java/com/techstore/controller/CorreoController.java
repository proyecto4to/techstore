package com.techstore.controller;

import com.techstore.dto.response.CorreoResponse;
import com.techstore.dto.response.PageResponse;
import com.techstore.service.CorreoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/correos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('Admin','Cajero')")
@Tag(name = "Correos", description = "Bandeja de salida de notificaciones por correo")
public class CorreoController {

    private final CorreoService correoService;

    @GetMapping
    @Operation(summary = "Listar correos enviados/registrados (bandeja de salida)")
    public PageResponse<CorreoResponse> listar(
            @ParameterObject @PageableDefault(size = 15, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return correoService.listar(pageable);
    }
}
