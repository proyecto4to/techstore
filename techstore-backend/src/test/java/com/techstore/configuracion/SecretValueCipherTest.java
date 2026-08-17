package com.techstore.configuracion;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretValueCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void cifraConNonceYAutenticaElContenido() {
        SecretValueCipher cipher = new SecretValueCipher(KEY);

        String primero = cipher.cifrar("clave smtp secreta");
        String segundo = cipher.cifrar("clave smtp secreta");

        assertTrue(primero.startsWith(SecretValueCipher.PREFIX));
        assertNotEquals(primero, segundo, "cada cifrado debe usar un nonce distinto");
        assertEquals("clave smtp secreta", cipher.descifrar(primero));
    }

    @Test
    void detectaUnaAlteracionDelCiphertext() {
        SecretValueCipher cipher = new SecretValueCipher(KEY);
        String cifrado = cipher.cifrar("secreto");
        byte[] envelope = Base64.getUrlDecoder().decode(cifrado.substring(SecretValueCipher.PREFIX.length()));
        envelope[12] ^= 1;
        String alterado = SecretValueCipher.PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(envelope);

        assertThrows(IllegalStateException.class, () -> cipher.descifrar(alterado));
    }

    @Test
    void exigeUnaClaveDe256Bits() {
        String corta = Base64.getEncoder().encodeToString("muy-corta".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> new SecretValueCipher(corta));
    }
}
