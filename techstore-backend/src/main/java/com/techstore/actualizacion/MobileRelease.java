package com.techstore.actualizacion;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Una versión de la aplicación Android publicada por este servidor.
 *
 * <p>Sale de {@code release-metadata.json}, el archivo que ya deja
 * {@code scripts/build-mobile-release.ps1} junto al APK. Publicar una versión
 * es, entonces, compilar: no hay un segundo lugar donde anotar la versión a
 * mano y desincronizarse.
 */
public record MobileRelease(
        String versionName,
        int versionCode,
        String applicationId,
        String sha256,
        long sizeBytes,
        Instant publishedAt,
        List<String> notes,
        Path apk) {
}
