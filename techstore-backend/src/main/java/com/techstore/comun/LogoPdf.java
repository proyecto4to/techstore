package com.techstore.comun;

import com.lowagie.text.Image;

import java.io.InputStream;

/**
 * Isotipo de TechStore para los PDF (factura, nota de crédito y KuDE).
 *
 * <p>Se usa el isotipo solo, sin el nombre: la razón social se imprime aparte y
 * es editable desde Admin → Empresa, así que un logo con el nombre adentro
 * quedaría contradiciendo al documento si el negocio se renombra.
 *
 * <p>Va en PNG porque openpdf no dibuja SVG. El original vectorial está en
 * {@code techstore-frontend/public/isotipo.svg}; si se cambia, hay que volver a
 * exportar este PNG.
 */
public final class LogoPdf {

    private static final String RECURSO = "/logo/isotipo.png";

    /** Se lee una sola vez: el mismo PNG sirve para todos los documentos. */
    private static volatile byte[] contenido;
    private static volatile boolean intentado;

    private LogoPdf() {
    }

    /**
     * Isotipo escalado a {@code alto} puntos, o {@code null} si no se pudo
     * cargar. Devuelve null en vez de fallar a propósito: que falte el logo no
     * puede impedir emitir una factura.
     */
    public static Image imagen(float alto) {
        byte[] bytes = cargar();
        if (bytes == null) {
            return null;
        }
        try {
            Image imagen = Image.getInstance(bytes);
            imagen.scaleToFit(alto, alto);
            return imagen;
        } catch (Exception ex) {
            return null;
        }
    }

    private static byte[] cargar() {
        if (!intentado) {
            synchronized (LogoPdf.class) {
                if (!intentado) {
                    try (InputStream in = LogoPdf.class.getResourceAsStream(RECURSO)) {
                        contenido = in == null ? null : in.readAllBytes();
                    } catch (Exception ex) {
                        contenido = null;
                    }
                    intentado = true;
                }
            }
        }
        return contenido;
    }
}
