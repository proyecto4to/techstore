package com.techstore.integracion;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Un PostgreSQL de verdad para las pruebas de integración.
 *
 * <p>Levanta el binario oficial de Postgres como proceso hijo. Es el mismo motor
 * que corre en producción, así que las migraciones se prueban con la sintaxis y
 * los tipos reales —{@code jsonb}, enums nativos, {@code Row Level Security}—,
 * cosa que una base en memoria como H2 no puede hacer.
 *
 * <p>No se usa Testcontainers porque Docker Desktop en Windows Home necesita
 * WSL2, y eso exige privilegios de administrador y un reinicio. Esta vía no
 * necesita nada. Si algún día hay Docker —por ejemplo al montar CI—, migrar es
 * cambiar el cuerpo de esta clase y nada más: el resto de los tests solo ve una
 * URL JDBC inyectada por {@link #registrar}.
 *
 * <p>La instancia es <b>única para toda la corrida</b>: arrancar Postgres cuesta
 * unos segundos y repetirlo por cada clase de test haría la suite inusable. Se
 * apaga sola cuando termina la JVM.
 */
public final class BaseDatosDePrueba {

    private static volatile EmbeddedPostgres instancia;

    private BaseDatosDePrueba() {
    }

    private static EmbeddedPostgres instancia() {
        if (instancia == null) {
            synchronized (BaseDatosDePrueba.class) {
                if (instancia == null) {
                    try {
                        instancia = EmbeddedPostgres.builder().start();
                        Runtime.getRuntime().addShutdownHook(new Thread(BaseDatosDePrueba::cerrar));
                    } catch (IOException ex) {
                        throw new UncheckedIOException("No se pudo levantar el PostgreSQL de pruebas", ex);
                    }
                }
            }
        }
        return instancia;
    }

    private static void cerrar() {
        EmbeddedPostgres actual = instancia;
        if (actual != null) {
            try {
                actual.close();
            } catch (IOException ignorada) {
                // La JVM se está apagando: que no se pueda cerrar el proceso no
                // cambia el resultado de la corrida.
            }
        }
    }

    /**
     * Apunta el contexto de Spring a esta base. Se invoca desde un método
     * {@code @DynamicPropertySource} en el test.
     */
    public static void registrar(DynamicPropertyRegistry registro) {
        EmbeddedPostgres pg = instancia();
        registro.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        registro.add("spring.datasource.username", () -> "postgres");
        registro.add("spring.datasource.password", () -> "postgres");
        registro.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Spring cachea un contexto por clase de test y cada uno se queda con su
        // pool vivo hasta el final de la corrida. Con el tamaño por omisión —diez
        // conexiones— la suite terminó pidiéndole al Postgres embebido más de las
        // cien que admite, y las últimas clases fallaban al arrancar con «ya
        // tenemos demasiados clientes»: un fallo de infraestructura que se leía
        // como un test roto. Cada contexto usa MockMvc en un solo hilo, así que
        // con tres alcanza, y las inactivas se sueltan solas.
        registro.add("spring.datasource.hikari.maximum-pool-size", () -> "3");
        registro.add("spring.datasource.hikari.minimum-idle", () -> "0");
        registro.add("spring.datasource.hikari.idle-timeout", () -> "10000");
        // Flyway es el dueño del esquema, también acá: la gracia de estas pruebas
        // es justamente que las migraciones corran de verdad.
        registro.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registro.add("spring.flyway.enabled", () -> "true");
        // Flyway tiene su propia conexión (en producción migra con el rol
        // administrador mientras la app corre con uno limitado). Hay que
        // apuntarla también acá: si no, se queda con el valor de
        // application.properties e intenta conectarse a la base real de la
        // máquina en vez de a esta.
        registro.add("spring.flyway.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        registro.add("spring.flyway.user", () -> "postgres");
        registro.add("spring.flyway.password", () -> "postgres");
    }
}
