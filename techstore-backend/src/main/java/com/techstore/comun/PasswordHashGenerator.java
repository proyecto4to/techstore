package com.techstore.comun;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilidad de línea de comandos para generar hashes BCrypt
 * (por ejemplo, para sembrar usuarios en migraciones Flyway).
 *
 * Uso: java ... com.techstore.comun.PasswordHashGenerator <password>
 */
public final class PasswordHashGenerator {

    private PasswordHashGenerator() {
    }

    public static void main(String[] args) {
        String raw = args.length > 0 ? args[0] : "admin123";
        System.out.println(new BCryptPasswordEncoder().encode(raw));
    }
}
