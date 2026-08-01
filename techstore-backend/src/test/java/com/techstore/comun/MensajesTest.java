package com.techstore.comun;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cuida los textos que lee el usuario.
 *
 * <p>Extraerlos del código los volvió invisibles para el compilador: una clave
 * mal escrita ya no rompe el build, aparece como texto crudo en la pantalla de
 * alguien. Estos tests son lo que reemplaza a esa verificación perdida.
 */
@DisplayName("Textos de la interfaz")
class MensajesTest {

    private static final Path RAIZ_CODIGO = Path.of("src/main/java/com/techstore");
    private static final Pattern CLAVE_EN_EXCEPCION = Pattern.compile(
            "new (?:BusinessException|ResourceNotFoundException|CuentaInactivaException"
                    + "|TooManyAttemptsException)\\(\\s*\"([a-z][\\w.]*)\"");
    private static final Pattern CLAVE_EN_VALIDACION = Pattern.compile("message\\s*=\\s*\"\\{([\\w.]+)}\"");

    private static Properties cargar(String archivo) {
        Properties props = new Properties();
        try (InputStream in = MensajesTest.class.getResourceAsStream("/i18n/" + archivo)) {
            assertTrue(in != null, "Falta el archivo de mensajes " + archivo);
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return props;
    }

    /** Todas las claves que el código pide, leídas del propio código. */
    private static Set<String> clavesUsadas() {
        Set<String> claves = new TreeSet<>();
        try (Stream<Path> archivos = Files.walk(RAIZ_CODIGO)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String texto = Files.readString(archivo, StandardCharsets.UTF_8);
                for (Pattern patron : List.of(CLAVE_EN_EXCEPCION, CLAVE_EN_VALIDACION)) {
                    Matcher m = patron.matcher(texto);
                    while (m.find()) {
                        claves.add(m.group(1));
                    }
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return claves;
    }

    @Test
    @DisplayName("toda clave que el código lanza existe en el properties")
    void noHayClavesSinTexto() {
        Properties es = cargar("messages.properties");
        Set<String> usadas = clavesUsadas();

        // Sin esto el test pasaría igual si el escaneo dejara de encontrar
        // código —por un cambio de ruta o de patrón—, y daría por cubierto
        // justo lo que dejó de mirar.
        assertTrue(usadas.size() > 40,
                "El escaneo encontró solo " + usadas.size() + " claves: no está leyendo el código");

        Set<String> faltantes = new TreeSet<>();
        for (String clave : usadas) {
            if (!es.containsKey(clave)) {
                faltantes.add(clave);
            }
        }
        assertEquals(Set.of(), faltantes,
                "El código lanza estas claves y no tienen texto: el usuario vería la clave cruda");
    }

    @Test
    @DisplayName("el inglés cubre exactamente las mismas claves que el español")
    void losIdiomasEstanParejos() {
        Properties es = cargar("messages.properties");
        Properties en = cargar("messages_en.properties");

        Set<String> sinTraducir = new TreeSet<>(es.stringPropertyNames());
        sinTraducir.removeAll(en.stringPropertyNames());
        assertEquals(Set.of(), sinTraducir, "Claves sin traducir al inglés");

        // Al revés también importa: una clave que sobra en inglés suele ser una
        // que se renombró en español y quedó huérfana.
        Set<String> sobrantes = new TreeSet<>(en.stringPropertyNames());
        sobrantes.removeAll(es.stringPropertyNames());
        assertEquals(Set.of(), sobrantes, "Claves en inglés que ya no existen en español");
    }

    @Test
    @DisplayName("cada idioma espera la misma cantidad de argumentos")
    void losArgumentosCoinciden() {
        Properties es = cargar("messages.properties");
        Properties en = cargar("messages_en.properties");

        // Un {1} que falte en una traducción no rompe nada al compilar: se pierde
        // el dato en pantalla y solo en ese idioma, que es de lo más difícil de
        // notar mirando.
        Set<String> distintos = new TreeSet<>();
        for (String clave : es.stringPropertyNames()) {
            if (argumentos(es.getProperty(clave)) != argumentos(en.getProperty(clave, ""))) {
                distintos.add(clave);
            }
        }
        assertEquals(Set.of(), distintos, "El español y el inglés usan distinta cantidad de argumentos");
    }

    @Test
    @DisplayName("ninguna plantilla le exige un tipo al argumento")
    void losArgumentosNoLlevanFormato() {
        // Un {0,number} obliga a que ese argumento sea siempre numérico y tira
        // IllegalArgumentException si le llega un texto, justo mientras se
        // armaba un mensaje de error. Los enteros los convierte Mensajes antes
        // de formatear, así que acá no hace falta —ni conviene— declarar tipos.
        Set<String> conFormato = new TreeSet<>();
        for (Properties idioma : List.of(cargar("messages.properties"), cargar("messages_en.properties"))) {
            for (String clave : idioma.stringPropertyNames()) {
                if (Pattern.compile("\\{\\d+,").matcher(idioma.getProperty(clave)).find()) {
                    conFormato.add(clave);
                }
            }
        }
        assertEquals(Set.of(), conFormato, "Estas plantillas le exigen un tipo al argumento");
    }

    @Test
    @DisplayName("los mensajes con argumentos se formatean sin romperse")
    void seFormateanSinRomperse() {
        // La trampa de MessageFormat: en un mensaje con argumentos, una comilla
        // simple sin duplicar se come el texto que sigue. Se detecta formateando
        // de verdad y comprobando que no se haya perdido ningún argumento.
        for (Properties idioma : List.of(cargar("messages.properties"), cargar("messages_en.properties"))) {
            for (String clave : idioma.stringPropertyNames()) {
                String plantilla = idioma.getProperty(clave);
                int cuantos = argumentos(plantilla);
                if (cuantos == 0) {
                    continue;
                }
                Object[] valores = new Object[cuantos];
                for (int i = 0; i < cuantos; i++) {
                    valores[i] = "«" + i + "»";
                }
                String armado = new MessageFormat(plantilla).format(valores);
                for (int i = 0; i < cuantos; i++) {
                    assertTrue(armado.contains("«" + i + "»"),
                            "El argumento {%d} de '%s' se perdió al formatear: %s"
                                    .formatted(i, clave, armado));
                }
            }
        }
    }

    /**
     * Cuántos argumentos posicionales usa la plantilla. Cuenta tanto {@code {0}}
     * como {@code {0,number,#}}.
     */
    private static int argumentos(String plantilla) {
        Matcher m = Pattern.compile("\\{(\\d+)[,}]").matcher(plantilla);
        int maximo = -1;
        while (m.find()) {
            maximo = Math.max(maximo, Integer.parseInt(m.group(1)));
        }
        return maximo + 1;
    }
}
