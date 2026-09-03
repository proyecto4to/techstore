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
        Path releaseDir) {

    public MobileUpdateProperties {
        if (minimumVersionCode < 1 || latestVersionCode < minimumVersionCode) {
            throw new IllegalArgumentException("La política de versiones Mobile es inválida");
        }
        if (playStoreUrl == null || !"https".equalsIgnoreCase(playStoreUrl.getScheme())) {
            throw new IllegalArgumentException("app.mobile-update.play-store-url debe usar HTTPS");
        }
    }
}
