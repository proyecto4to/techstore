package com.techstore.notificaciones.outbox;

import java.util.regex.Pattern;

final class OutboxErrorSanitizer {
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/=-]+");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|token|authorization|secret|api[-_ ]?key)\\s*[:=]\\s*[^,; ]+");

    private OutboxErrorSanitizer() { }

    static String sanitize(Throwable error) {
        String value = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "Fallo de entrega sin detalle"
                : error.getMessage();
        value = BEARER.matcher(value).replaceAll("Bearer [REDACTED]");
        value = SECRET.matcher(value).replaceAll("$1=[REDACTED]");
        value = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
