package com.techstore.configuracion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Cifra secretos guardados en la base mediante AES-256-GCM. */
@Component
public class SecretValueCipher {

    static final String PREFIX = "enc:v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] AAD = "TechStore/configuracion/v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretValueCipher(@Value("${app.secrets.encryption-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null;
            return;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "APP_DATA_ENCRYPTION_KEY debe ser Base64 válido de 32 bytes", ex);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException(
                    "APP_DATA_ENCRYPTION_KEY debe decodificar exactamente 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public boolean habilitado() {
        return key != null;
    }

    public boolean estaCifrado(String valor) {
        return valor != null && valor.startsWith(PREFIX);
    }

    /**
     * Cifra el valor cuando hay clave configurada. Sin clave lo devuelve tal
     * cual: la instalación local no define {@code APP_DATA_ENCRYPTION_KEY} y
     * exigirla acá haría explotar el guardado de la configuración SMTP desde
     * Admin. Producción no llega nunca a este caso, porque
     * {@code application-prod.properties} pide la variable sin valor por
     * defecto y {@link SecretosProduccionGuard} se niega a arrancar sin ella.
     */
    public String cifrar(String valor) {
        if (valor == null || valor.isBlank() || key == null) {
            return valor;
        }
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, envelope, nonce.length, ciphertext.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(envelope);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el secreto de configuración", ex);
        }
    }

    /**
     * Descifra el formato actual. Un valor antiguo sin prefijo se devuelve tal
     * cual para permitir que el runner de producción lo migre una sola vez.
     */
    public String descifrar(String valor) {
        if (valor == null || valor.isBlank() || !estaCifrado(valor)) {
            return valor;
        }
        exigirClave();
        try {
            byte[] envelope = Base64.getUrlDecoder().decode(valor.substring(PREFIX.length()));
            if (envelope.length <= NONCE_BYTES + 16) {
                throw new IllegalArgumentException("Secreto cifrado incompleto");
            }
            byte[] nonce = Arrays.copyOfRange(envelope, 0, NONCE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(envelope, NONCE_BYTES, envelope.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException ex) {
            throw new IllegalStateException(
                    "No se pudo autenticar el secreto: la clave cambió o el dato fue alterado", ex);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("No se pudo descifrar el secreto de configuración", ex);
        }
    }

    private void exigirClave() {
        if (key == null) {
            throw new IllegalStateException(
                    "Falta APP_DATA_ENCRYPTION_KEY para proteger los secretos guardados");
        }
    }
}
