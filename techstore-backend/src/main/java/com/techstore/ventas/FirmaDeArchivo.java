package com.techstore.ventas;

/**
 * Si los primeros bytes de un archivo corresponden al tipo que dice tener.
 *
 * <p>El {@code contentType} lo manda quien sube: sin esta comprobación,
 * cualquier archivo puede decir que es una imagen.
 */
final class FirmaDeArchivo {

    private FirmaDeArchivo() {
    }

    static boolean coincide(String contentType, byte[] bytes) {
        if (contentType == null || bytes == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                    && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "application/pdf" -> bytes.length >= 5
                    && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
            default -> false;
        };
    }
}
