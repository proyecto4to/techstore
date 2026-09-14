package com.techstore.seguridad;

import java.util.List;

/**
 * Lo que una persona puede hacer de verdad, ya resuelto.
 *
 * <p>Es la misma respuesta que consulta el administrador sobre otro usuario y
 * la que cada cliente pide para sí mismo al iniciar sesión. Una sola forma para
 * los dos casos, porque son la misma pregunta: la Web y el APK no deciden nada
 * por su cuenta, dibujan lo que dice el servidor.
 *
 * @param permitidos atajo plano —{@code "productos.ver"}— para que el cliente
 *                   pregunte por una acción sin recorrer la matriz entera
 * @param pantallas  las pantallas habilitadas para su rol, por nombre
 */
public record PermisosEfectivosResponse(
        Long usuarioId,
        String email,
        String nombre,
        String rol,
        Boolean activo,
        List<FilaModulo> modulos,
        List<String> pantallas,
        List<String> permitidos) {

    public record FilaModulo(String modulo, String etiqueta, List<AccionEfectiva> acciones) { }

    /**
     * @param origen   constante para que el cliente decida cómo mostrarlo
     * @param etiquetaOrigen texto ya en castellano, para no traducir en cada pantalla
     * @param motivo   por qué se hizo la excepción; nulo cuando no hay excepción
     */
    public record AccionEfectiva(
            String accion,
            String etiqueta,
            boolean permitido,
            String origen,
            String etiquetaOrigen,
            String motivo) { }
}
