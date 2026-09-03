package com.techstore.actualizacion;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Política de actualización que consume la aplicación Android.
 *
 * <p>Los campos del release solo vienen cuando este servidor publica el APK
 * ({@code distribution = SELF_HOSTED}). Se agregaron al final y son opcionales
 * a propósito: una versión instalada vieja, que no los conoce, los ignora y
 * sigue funcionando con la política de siempre.
 */
public record MobileVersionResponse(
        String platform,
        int currentVersionCode,
        int minimumVersionCode,
        int latestVersionCode,
        boolean updateAvailable,
        boolean mandatory,
        String distribution,
        URI storeUrl,
        String latestVersionName,
        URI downloadUrl,
        String sha256,
        long sizeBytes,
        Instant publishedAt,
        String applicationId,
        List<String> notes) {

    /** Respuesta para cuando la actualización la reparte Google Play. */
    public static MobileVersionResponse play(
            String platform,
            int currentVersionCode,
            int minimumVersionCode,
            int latestVersionCode,
            URI storeUrl) {
        return new MobileVersionResponse(
                platform,
                currentVersionCode,
                minimumVersionCode,
                latestVersionCode,
                currentVersionCode < latestVersionCode,
                currentVersionCode < minimumVersionCode,
                "GOOGLE_PLAY",
                storeUrl,
                null,
                null,
                null,
                0L,
                null,
                null,
                List.of());
    }

    /** Respuesta para cuando el APK lo sirve este mismo servidor. */
    public static MobileVersionResponse selfHosted(
            String platform,
            int currentVersionCode,
            int minimumVersionCode,
            MobileRelease release,
            URI downloadUrl) {
        return new MobileVersionResponse(
                platform,
                currentVersionCode,
                minimumVersionCode,
                release.versionCode(),
                currentVersionCode < release.versionCode(),
                currentVersionCode < minimumVersionCode,
                "SELF_HOSTED",
                downloadUrl,
                release.versionName(),
                downloadUrl,
                release.sha256(),
                release.sizeBytes(),
                release.publishedAt(),
                release.applicationId(),
                release.notes());
    }
}
