package com.techstore.actualizacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.file.Path;

/**
 * @param releaseDir directorio donde este servidor publica el APK y su
 *                   {@code release-metadata.json}. Es opcional: sin él la app
 *                   sigue apuntando a Google Play, que es el camino previsto
 *                   para cuando la aplicación se publique en la tienda.
 */
@ConfigurationProperties(prefix = "app.mobile-update")
public record MobileUpdateProperties(
        int minimumVersionCode,
        int latestVersionCode,
        URI playStoreUrl,
        String releaseDir) {

    public MobileUpdateProperties {
        if (minimumVersionCode < 1 || latestVersionCode < minimumVersionCode) {
            throw new IllegalArgumentException("La política de versiones Mobile es inválida");
        }
        if (playStoreUrl == null || !"https".equalsIgnoreCase(playStoreUrl.getScheme())) {
            throw new IllegalArgumentException("app.mobile-update.play-store-url debe usar HTTPS");
        }
    }

    /**
     * El directorio como ruta del disco, o {@code null} si no se configuró.
     *
     * <p>Se enlaza como texto y se convierte acá a propósito. Enlazarlo
     * directamente a {@link Path} lo hace pasar por la resolución de recursos
     * de Spring, y en un contexto web servlet eso descarta cualquier ruta que
     * suba de directorio: el valor por defecto
     * {@code ../artifacts/mobile/release} se normaliza a {@code null} y el
     * contexto no arranca. Acá no es un recurso de la aplicación sino una
     * carpeta del disco, relativa al directorio de trabajo del proceso.
     */
    public Path releaseDirPath() {
        return releaseDir == null || releaseDir.isBlank() ? null : Path.of(releaseDir.trim());
    }
}
