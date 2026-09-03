package com.techstore.actualizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lee la versión Android publicada en el directorio de artefactos.
 *
 * <p>El directorio es el que llena {@code scripts/build-mobile-release.ps1}: el
 * APK y su {@code release-metadata.json} con versión, hash y ruta. Acá solo se
 * valida y se expone; nada se genera. Publicar una versión es, entonces,
 * compilar: no hay un segundo lugar donde anotar la versión a mano y
 * desincronizarse.
 *
 * <p>El resultado se cachea contra el tamaño y la fecha del metadata, porque el
 * endpoint de versión se consulta cada vez que alguien abre la aplicación y
 * releer y validar el archivo en cada llamada no aporta nada.
 */
@Slf4j
@Service
public class MobileReleaseService {

    private static final String METADATA = "release-metadata.json";
    private static final String CHANGELOG = "changelog.json";

    private final MobileUpdateProperties properties;
    private final ObjectMapper mapper;

    private volatile Cache cache;

    public MobileReleaseService(MobileUpdateProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    private record Cache(long tamano, long modificado, MobileRelease release) {
    }

    public Optional<MobileRelease> disponible() {
        Path directorio = properties.releaseDirPath();
        if (directorio == null) {
            return Optional.empty();
        }
        Path metadata = directorio.resolve(METADATA);
        try {
            if (!Files.isRegularFile(metadata)) {
                return Optional.empty();
            }
            long tamano = Files.size(metadata);
            long modificado = Files.getLastModifiedTime(metadata).toMillis();
            Cache actual = cache;
            if (actual != null && actual.tamano() == tamano && actual.modificado() == modificado) {
                return Optional.of(actual.release());
            }
            MobileRelease release = leer(directorio, metadata);
            cache = new Cache(tamano, modificado, release);
            return Optional.of(release);
        } catch (IOException | RuntimeException ex) {
            // Un metadata roto no puede tumbar el arranque de la aplicación móvil:
            // sin release publicada se responde la política de siempre.
            log.warn("No se pudo leer la versión publicada en {}: {}", metadata, ex.getMessage());
            return Optional.empty();
        }
    }

    private MobileRelease leer(Path directorio, Path metadata) throws IOException {
        JsonNode json = mapper.readTree(Files.readString(metadata, StandardCharsets.UTF_8));
        Path apk = resolverApk(directorio, json);
        if (!Files.isRegularFile(apk)) {
            throw new IllegalStateException("El APK declarado no existe: " + apk);
        }
        String sha = texto(json, "apkSha256");
        if (sha == null || !sha.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException("apkSha256 ausente o con formato invalido");
        }
        int versionCode = json.path("versionCode").asInt(0);
        if (versionCode < 1) {
            throw new IllegalStateException("versionCode invalido");
        }
        return new MobileRelease(
                texto(json, "versionName"),
                versionCode,
                texto(json, "applicationId"),
                sha.toLowerCase(),
                Files.size(apk),
                fecha(json),
                cambios(directorio),
                apk);
    }

    /**
     * El metadata guarda la ruta absoluta de la máquina que compiló. Se usa solo
     * el nombre del archivo, resuelto dentro del directorio configurado: así el
     * artefacto sigue sirviendo aunque se lo haya copiado a otro equipo.
     */
    private Path resolverApk(Path directorio, JsonNode json) {
        String declarado = texto(json, "apk");
        if (declarado == null || declarado.isBlank()) {
            throw new IllegalStateException("El metadata no declara el APK");
        }
        String nombre = Path.of(declarado.replace('\\', '/')).getFileName().toString();
        return directorio.resolve(nombre).normalize();
    }

    private Instant fecha(JsonNode json) {
        String valor = texto(json, "buildDateUtc");
        try {
            return valor == null ? Instant.EPOCH : Instant.parse(valor);
        } catch (RuntimeException ex) {
            return Instant.EPOCH;
        }
    }

    /** Las notas son opcionales: sin changelog, la pantalla no muestra la lista. */
    private List<String> cambios(Path directorio) {
        Path archivo = directorio.resolve(CHANGELOG);
        if (!Files.isRegularFile(archivo)) {
            return List.of();
        }
        try {
            JsonNode json = mapper.readTree(Files.readString(archivo, StandardCharsets.UTF_8));
            JsonNode lista = json.isArray() ? json : json.path("cambios");
            List<String> notas = new ArrayList<>();
            lista.forEach(nodo -> {
                String texto = nodo.asText("").trim();
                if (!texto.isEmpty()) {
                    notas.add(texto);
                }
            });
            return List.copyOf(notas);
        } catch (IOException | RuntimeException ex) {
            log.warn("Changelog ilegible en {}: {}", archivo, ex.getMessage());
            return List.of();
        }
    }

    private String texto(JsonNode json, String campo) {
        JsonNode nodo = json.get(campo);
        return nodo == null || nodo.isNull() ? null : nodo.asText();
    }
}
