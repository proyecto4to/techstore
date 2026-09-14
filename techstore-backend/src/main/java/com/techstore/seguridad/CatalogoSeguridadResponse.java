package com.techstore.seguridad;

import java.util.List;

/**
 * Qué módulos y qué acciones existen para configurar.
 *
 * <p>La pantalla de Seguridad se dibuja con esto y no con una lista escrita en
 * el cliente: agregar un módulo al backend lo hace aparecer en la Web y en el
 * APK sin tocar ninguno de los dos.
 *
 * @param rutas de qué módulo es cada prefijo de la API, para que quien
 *              administra pueda ver qué está gobernando cada fila en lugar de
 *              adivinarlo por el nombre
 */
public record CatalogoSeguridadResponse(
        List<ModuloDisponible> modulos,
        List<AccionDisponible> acciones,
        List<String> pantallas,
        List<RutaDeModulo> rutas) {

    public record ModuloDisponible(String modulo, String etiqueta) { }

    public record AccionDisponible(String accion, String etiqueta) { }

    public record RutaDeModulo(String prefijo, String modulo) { }
}
