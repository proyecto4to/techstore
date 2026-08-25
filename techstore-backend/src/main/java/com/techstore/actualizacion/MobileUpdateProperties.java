package com.techstore.actualizacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "app.mobile-update")
public record MobileUpdateProperties(
        int minimumVersionCode,
        int latestVersionCode,
        URI playStoreUrl) {

    public MobileUpdateProperties {
        if (minimumVersionCode < 1 || latestVersionCode < minimumVersionCode) {
            throw new IllegalArgumentException("La política de versiones Mobile es inválida");
        }
        if (playStoreUrl == null || !"https".equalsIgnoreCase(playStoreUrl.getScheme())) {
            throw new IllegalArgumentException("app.mobile-update.play-store-url debe usar HTTPS");
        }
    }
}
