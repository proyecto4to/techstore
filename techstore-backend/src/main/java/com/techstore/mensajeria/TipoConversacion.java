package com.techstore.mensajeria;

/**
 * Con quién se habla del otro lado.
 *
 * <p>La distinción no es cosmética: decide quién puede leer la conversación.
 */
public enum TipoConversacion {
    /**
     * Cliente ↔ tienda. El cliente la ve como «Atención al Cliente» y no sabe
     * quién le responde; del otro lado la atiende cualquiera del mostrador.
     */
    SOPORTE,

    /** Dos personas concretas. Nadie más la lee, ni siquiera un administrador. */
    PRIVADA
}
