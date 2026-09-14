package com.techstore.auditoria;

import com.techstore.comun.FiltroOpcional;
import com.techstore.comun.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Consulta del rastro de auditoría. Es solo lectura a propósito: no hay
 * endpoint para editar ni borrar, porque un registro que se puede modificar no
 * sirve como prueba de nada.
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Admin')")
@Tag(name = "Auditoría", description = "Quién hizo qué y cuándo. Solo lectura, solo Admin.")
public class AuditoriaController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @Operation(summary = "Listar acciones auditadas (Admin)",
            description = "Filtros opcionales por entidad, usuario, acción, rol y rango de fechas "
                    + "(inclusivo). El rol es el que tenía el autor al hacer la acción. "
                    + "Resultado acepta Permitido o Rechazado; plataforma, Web, Movil o Api")
    public PageResponse<AuditoriaResponse> listar(
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) String plataforma,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC)
            Pageable pageable) {

        LocalDateTime inicio = desde == null ? null : desde.atStartOfDay();
        // El rango es inclusivo: "hasta el 30" tiene que incluir todo el día 30.
        LocalDateTime fin = hasta == null ? null : hasta.plusDays(1).atStartOfDay();

        return PageResponse.from(auditLogRepository
                .findAll(AuditLogSpecs.conFiltros(
                        entidad, FiltroOpcional.id(usuarioId), accion, rol, inicio, fin,
                        vacioComoNulo(resultado), vacioComoNulo(plataforma)), pageable)
                .map(AuditoriaResponse::from));
    }

    @GetMapping("/entidades")
    @Operation(summary = "Entidades con registros de auditoría (Admin)",
            description = "Para poblar el filtro sin adivinar qué se auditó.")
    public List<String> entidades() {
        return auditLogRepository.entidadesRegistradas();
    }

    private static String vacioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
