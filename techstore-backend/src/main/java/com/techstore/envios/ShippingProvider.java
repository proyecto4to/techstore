package com.techstore.envios;

import com.techstore.catalogo.Producto;
import com.techstore.comun.Dinero;
import com.techstore.direcciones.DireccionEntrega;

import java.util.List;
import java.util.Map;

public interface ShippingProvider {
    String codigo();
    List<CotizacionEnvio> cotizar(DireccionEntrega direccion, Map<Producto, Integer> items, Dinero subtotal);
}
