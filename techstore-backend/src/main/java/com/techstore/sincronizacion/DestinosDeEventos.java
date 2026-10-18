package com.techstore.sincronizacion;

import java.util.Set;

/**
 * A qué canal va cada evento de dominio, según quién tiene derecho a verlo.
 *
 * <p>El flujo de eventos se publicaba en un único destino por empresa, y el
 * interceptor de STOMP solo comprobaba que el suscriptor perteneciera a esa
 * empresa. O sea que un cliente conectado recibía <em>todo</em>: movimientos de
 * caja, pagos, facturas, altas y bajas de usuarios. El cuerpo del evento es
 * flaco —la operación y el id—, pero la sola llegada ya cuenta que el negocio
 * cobró, que emitió una factura o que dio de alta a alguien, con su hora.
 *
 * <p>Ese mismo filtro ya existía en el endpoint REST de recuperación por
 * cursor. Tenerlo en un camino y no en el otro no protege nada: alcanza con
 * quedarse escuchando el WebSocket. Ahora los dos usan esta clase.
 */
public final class DestinosDeEventos {

    /**
     * Lo que puede ver quien no opera la tienda.
     *
     * <p>Catálogo, porque el stock y los precios son públicos. Pedido y envío,
     * porque quien compra necesita ver avanzar el suyo. Esos dos siguen
     * revelando que <em>algún</em> pedido se movió: el evento no trae dueño, y
     * acotarlo más pide una columna nueva en la bitácora.
     */
    public static final Set<String> PARA_QUIEN_COMPRA =
            Set.of("PRODUCT", "BRAND", "MODEL", "ORDER", "ORDER_ITEM", "SHIPMENT", "PROMOTION");

    private DestinosDeEventos() {
    }

    /** Todo el flujo. Solo se suscribe quien opera la tienda. */
    public static String deLaTienda(Long tenantId) {
        return "/topic/tenant/" + tenantId + "/events";
    }

    /** El subconjunto público. Cualquiera de la empresa puede escucharlo. */
    public static String deQuienCompra(Long tenantId) {
        return "/topic/tenant/" + tenantId + "/events/compra";
    }

    /** Si este evento también viaja por el canal de quien compra. */
    public static boolean visibleParaQuienCompra(String agregado) {
        return PARA_QUIEN_COMPRA.contains(agregado);
    }
}
