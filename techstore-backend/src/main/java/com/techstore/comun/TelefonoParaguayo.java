package com.techstore.comun;

/** Normaliza teléfonos paraguayos sin obligar a escribir el prefijo +595. */
public final class TelefonoParaguayo {

    private TelefonoParaguayo() {
    }

    /**
     * Acepta formatos habituales como {@code 0981 123 456}, {@code 981123456}
     * o {@code +595 981 123 456}. Devuelve E.164 o {@code null} si no es usable.
     */
    public static String normalizar(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return null;
        }
        String digitos = telefono.replaceAll("\\D", "");
        if (digitos.startsWith("00595")) {
            digitos = digitos.substring(5);
        } else if (digitos.startsWith("595")) {
            digitos = digitos.substring(3);
        } else if (digitos.startsWith("0")) {
            digitos = digitos.substring(1);
        }
        if (digitos.length() < 8 || digitos.length() > 10) {
            return null;
        }
        return "+595" + digitos;
    }
}

