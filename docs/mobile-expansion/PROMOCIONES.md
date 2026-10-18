# PROMOCIONES

Un descuento porcentual, con fecha de inicio y de fin, que administración enciende y apaga. Toca los tres lados del sistema: backend, app y web.

## Cómo se usa

Administración → **Promociones** (también accesible con permiso del módulo Productos). El botón «Nueva» pide cuatro cosas: nombre, porcentaje, a qué se aplica y entre qué fechas. El interruptor de cada tarjeta la enciende y la apaga sin editar nada.

La lista distingue **tres** estados, no dos:

| Estado | Qué significa |
| --- | --- |
| Apagada | El interruptor está en no. No descuenta nada. |
| Encendida, fuera de fecha | Está en sí, pero hoy no está entre `desde` y `hasta`. Tampoco descuenta. |
| Rigiendo ahora | Encendida y dentro de la ventana. Es la que baja los precios. |

## Las cuatro decisiones que explican el resto

1. **Solo porcentaje.** Un descuento fijo en guaraníes obliga a decidir qué pasa cuando supera el precio, y esa pregunta aparece meses después, cuando alguien cambia un precio. El tope es 90% para que un cero de más no regale el catálogo.
2. **Un solo alcance por promoción**: todo el catálogo, un producto o una marca. «Esta marca menos dos productos» se arma con dos promociones.
3. **Si dos se pisan, gana la de mayor descuento, y no se suman.** Sumar dos promociones razonables puede dejar el producto gratis, y el resultado dependería del orden en que se cargaron.
4. **El descuento se guarda en la línea del pedido**, en la misma columna que usa el descuento manual del mostrador (`pedido_detalle.descuento`). Por eso la factura ya lo muestra sin tocar nada, y un pedido de marzo conserva su importe aunque la promoción se apague en abril.

## Dónde vive cada cosa

| Pieza | Archivo |
| --- | --- |
| Tabla | `V57__promociones.sql` |
| La regla de precio, única para todos | `promociones/CalculadorDePrecios.java` |
| Alta, edición y encendido | `promociones/PromocionService.java` |
| API | `promociones/PromocionController.java` (`/api/v1/promociones`) |
| Pantalla de administración | `features/administration/presentation/promotions_page.dart` |
| Precio con rebaja en la app | `core/widgets/precio_con_promocion.dart` |
| Precio con rebaja en la web | `store/cartStore.js` (`tienePromocion`) + `ProductCard.jsx` |

## El contrato, y por qué está así

`precio` en el catálogo **ya viene con el descuento aplicado**: es lo que se va a cobrar. El precio de lista viaja aparte, en `precioLista`, junto con `promocion`.

```json
{
  "id": 6,
  "nombre": "AMD Ryzen 9 7950X",
  "precio": 3360000,
  "precioLista": 4200000,
  "promocion": { "id": 3, "nombre": "Liquidación", "porcentaje": 20, "hasta": "2026-09-30T23:59:00" }
}
```

Es deliberado que el descuento entre en `precio` y no solo en `promocion`. **Una app vieja, que no sabe que las promociones existen, muestra `precio` y muestra el correcto.** Si la rebaja viviera únicamente en el campo nuevo, esa app publicaría un precio más alto que el que después cobra el checkout, que es la peor forma de enterarse.

El mismo criterio vale para la pantalla: el número grande es siempre lo que se cobra, y el precio de lista solo aparece tachado al lado.

## Permisos

`/api/v1/promociones` cuelga del módulo **Productos** (`PermisoInterceptor`), no de un módulo propio: una promoción es el precio de un producto por unos días, así que quien puede cambiar precios puede descontarlos. Los endpoints exigen además rol Admin.

## Lo que queda afuera, y es a propósito

- **La venta directa del mostrador no aplica promociones.** Ahí el cajero carga el descuento a mano, que es lo que esa pantalla ya hacía; mezclar los dos descuentos en la misma línea haría imposible saber cuál fue cuál.
- **No hay pantalla de promociones en la web.** La web muestra los precios rebajados, pero se administran desde la app.
- **Una promoción no se borra: se apaga.** Borrarla no cambiaría ningún importe ya facturado, pero dejaría sin explicación por qué un pedido viejo salió más barato.
- **No hay cupones.** Una promoción la decide la tienda y alcanza a todos; un cupón es de una persona y necesita código, canje y control de uso.
