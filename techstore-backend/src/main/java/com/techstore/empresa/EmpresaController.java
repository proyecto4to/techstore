package com.techstore.empresa;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.techstore.usuarios.CambioActivoRequest;

import java.util.List;

@RestController
@RequestMapping("/api/v1/empresa")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Admin')")
@Tag(name = "Empresa", description = "Datos del emisor que salen en la factura y en el Documento Electrónico")
public class EmpresaController {

    private final EmpresaConfigService empresaConfigService;
    private final PuntoExpedicionService puntoExpedicionService;

    @GetMapping
    @Operation(summary = "Datos actuales del emisor")
    public EmpresaProperties actual() {
        return empresaConfigService.actual();
    }

    @GetMapping("/puntos-expedicion")
    @Operation(summary = "Puntos de expedición (cajas o sucursales que emiten)")
    public List<PuntoExpedicionResponse> puntosExpedicion() {
        return puntoExpedicionService.listar();
    }

    @PostMapping("/puntos-expedicion")
    @Operation(summary = "Agregar un punto de expedición",
            description = "Cada punto lleva su propia serie de numeración. El que ya venía usándose "
                    + "queda como el por defecto y conserva su numeración.")
    public PuntoExpedicionResponse crearPuntoExpedicion(@Valid @RequestBody PuntoExpedicionRequest request) {
        return puntoExpedicionService.crear(request);
    }

    @PutMapping("/puntos-expedicion/{id}/activo")
    @Operation(summary = "Activar o desactivar un punto de expedición")
    public PuntoExpedicionResponse cambiarActivoPunto(
            @PathVariable Long id, @Valid @RequestBody CambioActivoRequest request) {
        return puntoExpedicionService.cambiarActivo(id, request.activo());
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

    @GetMapping("/ubicacion")
    @Operation(summary = "Ver la ubicación de la empresa",
            description = "Devuelve definida=false mientras nadie la haya marcado en el mapa")
    public UbicacionEmpresaResponse ubicacion() {
        return empresaConfigService.ubicacion();
    }

    @PutMapping("/ubicacion")
    @Operation(summary = "Guardar la ubicación marcada en el mapa",
            description = "Latitud y longitud del punto elegido. Se valida el rango: una coma "
                    + "corrida guardaría un punto en medio del océano")
    public UbicacionEmpresaResponse guardarUbicacion(@Valid @RequestBody UbicacionEmpresaRequest request) {
        return empresaConfigService.guardarUbicacion(request);
    }
}
