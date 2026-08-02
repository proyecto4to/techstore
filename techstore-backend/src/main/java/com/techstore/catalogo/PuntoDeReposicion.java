package com.techstore.catalogo;

/**
 * Cuándo hay que volver a pedir un producto, y por qué.
 *
 * <p>El punto de reposición es cuánto se vende mientras se espera al proveedor,
 * más un colchón:
 *
 * <pre>{@code punto = demanda diaria × días de entrega + días de seguridad × demanda diaria}</pre>
 *
 * <p>Un número fijo para todo el catálogo no puede estar bien: el mismo 5 no
 * sirve para algo que se vende de a 20 por semana y llega en dos días, que para
 * algo que se vende de a 1 por mes y tarda un mes en llegar.
 *
 * @param unidades       stock disponible a partir del cual conviene reponer
 * @param demandaDiaria  unidades por día según el historial de ventas
 * @param diasEntrega    lo que tardó el proveedor las últimas veces
 * @param calculado      {@code false} si no había historial y se usó el mínimo
 *                       cargado a mano
 */
public record PuntoDeReposicion(
        int unidades,
        double demandaDiaria,
        double diasEntrega,
        boolean calculado) {

    /** Cuando no hay datos suficientes: se respeta el mínimo que cargó una persona. */
    public static PuntoDeReposicion manual(int unidades) {
        return new PuntoDeReposicion(unidades, 0, 0, false);
    }

    /** Si con este stock disponible ya corresponde pedir. */
    public boolean alcanzadoCon(int stockDisponible) {
        return stockDisponible <= unidades;
    }

    /**
     * Cuántos días de venta quedan con el stock actual. Es lo que hace
     * entendible el aviso: "te quedan 3 días" dice más que "te quedan 8
     * unidades". {@code null} cuando no se sabe cuánto se vende.
     */
    public Integer diasDeCobertura(int stockDisponible) {
        if (demandaDiaria <= 0) {
            return null;
        }
        return (int) Math.floor(stockDisponible / demandaDiaria);
    }
}
