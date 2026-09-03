package com.techstore.actualizacion;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

@Validated
@RestController
@RequestMapping("/api/v1/app")
@RequiredArgsConstructor
public class MobileVersionController {

    private static final MediaType APK = MediaType.parseMediaType("application/vnd.android.package-archive");

    /**
     * Ruta relativa a propósito: el cliente la resuelve contra su propia URL
     * base. Armar aquí una absoluta obligaría a confiar en las cabeceras del
     * proxy, y detrás del túnel eso devuelve la dirección interna.
     */
    private static final URI DESCARGA = URI.create("/api/v1/app/download");

    private final MobileUpdateProperties properties;
    private final MobileReleaseService releases;

    @GetMapping("/version")
    @Operation(summary = "Política de compatibilidad de la aplicación Mobile")
    public MobileVersionResponse version(
            @RequestParam @Pattern(regexp = "android") String platform,
            @RequestParam @Min(1) int versionCode) {
        Optional<MobileRelease> release = releases.disponible();
        if (release.isEmpty()) {
            return MobileVersionResponse.play(
                    platform,
                    versionCode,
                    properties.minimumVersionCode(),
                    properties.latestVersionCode(),
                    properties.playStoreUrl());
        }
        return MobileVersionResponse.selfHosted(
                platform,
                versionCode,
                properties.minimumVersionCode(),
                release.get(),
                DESCARGA);
    }

    /**
     * Entrega el APK publicado. Va sin autenticación por la misma razón que la
     * política: una actualización obligatoria tiene que poder aplicarse antes de
     * iniciar sesión, y una sesión vencida no puede dejar al usuario encerrado
     * en una versión incompatible.
     */
    @GetMapping("/download")
    @Operation(summary = "Descarga el APK publicado por este servidor")
    public ResponseEntity<Resource> descargar() {
        return releases.disponible()
                .<ResponseEntity<Resource>>map(release -> ResponseEntity.ok()
                        .contentType(APK)
                        .contentLength(release.sizeBytes())
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                .filename(release.apk().getFileName().toString())
                                .build()
                                .toString())
                        // El cliente verifica el hash igual, pero anunciarlo evita
                        // que un proxy sirva una copia vieja sin que nadie lo note.
                        .header("X-Apk-Sha256", release.sha256())
                        .body(new FileSystemResource(release.apk())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
