package com.techstore.actualizacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MobileVersionControllerTest {

    private static final URI PLAY = URI.create("https://play.google.com/store/apps/details?id=test");

    private MobileVersionController controller(Path releaseDir) {
        MobileUpdateProperties properties = new MobileUpdateProperties(5, 8, PLAY, releaseDir);
        return new MobileVersionController(
                properties, new MobileReleaseService(properties, new ObjectMapper()));
    }

    @Test
    void obligaActualizarSoloVersionesIncompatibles() {
        var controller = controller(null);

        var antigua = controller.version("android", 4);
        var compatible = controller.version("android", 6);
        var actual = controller.version("android", 8);

        assertThat(antigua.mandatory()).isTrue();
        assertThat(antigua.updateAvailable()).isTrue();
        assertThat(compatible.mandatory()).isFalse();
        assertThat(compatible.updateAvailable()).isTrue();
        assertThat(actual.updateAvailable()).isFalse();
        assertThat(actual.distribution()).isEqualTo("GOOGLE_PLAY");
    }

    @Test
    void publicaElApkCuandoHayUnReleaseEnElDirectorio(@TempDir Path dir) throws Exception {
        publicar(dir, 12, "1.0.11", "a".repeat(64));

        var respuesta = controller(dir).version("android", 8);

        assertThat(respuesta.distribution()).isEqualTo("SELF_HOSTED");
        // La versión sale del artefacto, no de la propiedad: publicar es compilar.
        assertThat(respuesta.latestVersionCode()).isEqualTo(12);
        assertThat(respuesta.latestVersionName()).isEqualTo("1.0.11");
        assertThat(respuesta.updateAvailable()).isTrue();
        assertThat(respuesta.sha256()).isEqualTo("a".repeat(64));
        assertThat(respuesta.sizeBytes()).isPositive();
        assertThat(respuesta.downloadUrl()).hasToString("/api/v1/app/download");
        assertThat(respuesta.applicationId()).isEqualTo("py.com.techstore.app");
    }

    @Test
    void noOfreceActualizacionSiElArtefactoEsLaVersionInstalada(@TempDir Path dir) throws Exception {
        publicar(dir, 8, "1.0.7", "b".repeat(64));

        var respuesta = controller(dir).version("android", 8);

        assertThat(respuesta.updateAvailable()).isFalse();
        assertThat(respuesta.mandatory()).isFalse();
    }

    @Test
    void vuelveAGooglePlaySiElMetadataEstaRoto(@TempDir Path dir) throws Exception {
        // Un artefacto a medio copiar no puede dejar sin respuesta a la app.
        Files.writeString(dir.resolve("release-metadata.json"), "{ esto no es json");

        var respuesta = controller(dir).version("android", 6);

        assertThat(respuesta.distribution()).isEqualTo("GOOGLE_PLAY");
        assertThat(respuesta.latestVersionCode()).isEqualTo(8);
    }

    @Test
    void vuelveAGooglePlaySiFaltaElApk(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("release-metadata.json"), """
                {"versionName":"1.0.11","versionCode":12,"applicationId":"py.com.techstore.app",
                 "apk":"C:\\\\build\\\\TechStore-1.0.11.apk","apkSha256":"%s"}
                """.formatted("c".repeat(64)));

        assertThat(controller(dir).version("android", 6).distribution()).isEqualTo("GOOGLE_PLAY");
    }

    @Test
    void rechazaUnHashConFormatoInvalido(@TempDir Path dir) throws Exception {
        publicar(dir, 12, "1.0.11", "no-es-un-sha256");

        assertThat(controller(dir).version("android", 6).distribution()).isEqualTo("GOOGLE_PLAY");
    }

    @Test
    void tomaLasNotasDelChangelogCuandoExiste(@TempDir Path dir) throws Exception {
        publicar(dir, 12, "1.0.11", "d".repeat(64));
        Files.writeString(dir.resolve("changelog.json"),
                "{\"cambios\":[\"Sistema de mapas\",\"  \",\"Correcciones generales\"]}");

        assertThat(controller(dir).version("android", 6).notes())
                .containsExactly("Sistema de mapas", "Correcciones generales");
    }

    @Test
    void entregaElApkPublicado(@TempDir Path dir) throws Exception {
        publicar(dir, 12, "1.0.11", "e".repeat(64));

        var respuesta = controller(dir).descargar();

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getHeaders().getFirst("X-Apk-Sha256")).isEqualTo("e".repeat(64));
        assertThat(respuesta.getHeaders().getContentType())
                .hasToString("application/vnd.android.package-archive");
    }

    @Test
    void responde404SiNoHayNadaPublicado() {
        assertThat(controller(null).descargar().getStatusCode().value()).isEqualTo(404);
    }

    /** Deja en el directorio lo mismo que deja build-mobile-release.ps1. */
    private void publicar(Path dir, int versionCode, String versionName, String sha) throws Exception {
        String apk = "TechStore-%s+%d.apk".formatted(versionName, versionCode);
        Files.write(dir.resolve(apk), new byte[] {0x50, 0x4B, 0x03, 0x04});
        Files.writeString(dir.resolve("release-metadata.json"), """
                {"versionName":"%s","versionCode":%d,"buildDateUtc":"2026-09-02T17:12:36.1127470Z",
                 "applicationId":"py.com.techstore.app",
                 "apk":"C:\\\\Users\\\\otra-maquina\\\\%s","apkSha256":"%s"}
                """.formatted(versionName, versionCode, apk, sha));
    }
}
