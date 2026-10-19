# Fase 6 — Sistema de movimiento

Fecha: 2026-10-19. Rama: `rediseno-monokai`.

## Principio

La marca se mueve como un cursor: aparece, se ubica y se queda quieta. Nada queda en bucle salvo un indicador de carga, que muere con la carga. Toda animación cumple una función —orientar, confirmar un toque, mostrar de dónde viene o adónde va algo— y ninguna es condición para usar la app: con la opción del sistema «Quitar animaciones» activada, todo funciona igual, sin desplazamientos.

Los valores viven en un solo lugar, [`lib/core/diseno/movimiento.dart`](../../techstore-mobile/lib/core/diseno/movimiento.dart):

| Token | Duración | Uso |
|---|---|---|
| `toque` | 120 ms | Respuesta física al apretar |
| `corto` | 200 ms | Cambio de estado de un control (favorito, cantidad, ícono) |
| `medio` | 320 ms | Cambio de contenido dentro de una pantalla (precio, stock, pasos) |
| `pagina` | 380 ms (vuelta: 300 ms) | Paso entre pantallas |
| `escena` | 620 ms | Presentación de un producto, avance del carrusel |
| `cascada` | 55 ms | Separación entre elementos que entran en fila |

Curvas: `llegada` (salida rápida, llegada suave, sin rebote), `salida` (arranca lento y se acelera) y `estandar`.

## Catálogo de animaciones

| Dónde | Qué hace | Con «Quitar animaciones» |
|---|---|---|
| Entre pantallas (`TransicionDeMarca`) | La nueva sube 3,5 % y se enfoca (escala 0,985 → 1) con fundido; la de atrás se aleja 4 % y se oscurece. No desliza de costado: sobre negro, la franja vacía parece un error | Solo fundido |
| Arranque (`PantallaDeArranque`) | Continúa el splash nativo con el isotipo en el mismo tamaño (137,3 dp) y lugar; el cursor late mientras se restaura la sesión | Quieto |
| Carga (`AppLoading`) | El cursor del isotipo late mientras dura la espera; se detiene al irse la carga | Quieto |
| Ingreso | El isotipo entra con el cursor deslizándose por el corte y parpadea una vez | Aparece armado |
| Vidriera | El eslogan se escribe solo y el cursor parpadea dos veces y se apaga (`TextoTecleado`); el carrusel principal pasa solo **una vuelta** cada 6 s, con paralaje de capas | Texto completo; el carrusel no avanza solo |
| Listas (vidriera, catálogo, favoritos) | Entrada en cascada (`Aparicion`) solo de lo que se ve al abrir —3 a 6 tarjetas—, una vez por sesión; lo que llega al desplazar entra directo | Directo |
| Tarjetas y botones (`Presionable`) | Se hunden al 97 % mientras se los aprieta | Sin hundimiento |
| Tarjeta → ficha | La foto viaja a la ficha (Hero) | Corte con fundido |
| Ficha | Doble toque acerca la foto 2,5× hacia el punto tocado | Salto directo |
| Agregar al carrito | La foto vuela al botón del carrito y el contador rebota; el botón confirma con su ícono | Sin vuelo ni rebote; la confirmación queda |
| Precio, stock y favorito | Cambian con un fundido corto (`AnimatedSwitcher`) | Cambio directo |
| Carrito | Deslizar una fila para quitarla; pide la misma confirmación que el tacho, para que un gesto involuntario no vacíe una línea | Igual: es un gesto, no una animación |
| Checkout | El indicador de pasos crece hacia el paso actual | Cambio directo |
| Catálogo | La píldora de la marca elegida se ilumina y el carril se desplaza solo hasta dejarla a la vista | Cambio directo |

## Qué se corrigió en esta fase

- **El carrusel de la vidriera giraba para siempre.** Ahora da una vuelta y se queda en el primer producto. Si la persona lo toca, deja de avanzar solo (antes reiniciaba la espera y seguía), y no se mueve con TalkBack u otro lector de pantalla activo: el contenido no puede cambiar mientras se lo están leyendo.
- **El zoom de la ficha se animaba aun con «Quitar animaciones».** Ahora salta directo.
- **La foto viajaba de la tarjeta a la ficha aun con «Quitar animaciones»**: Flutter no apaga los Hero por esa opción. Ahora, con ella activada, la ficha aparece con el mismo fundido que cualquier otra pantalla.

## Rendimiento

Las animaciones se armaron para no costar cuadros: nada de `BackdropFilter` ni `ShaderMask` en listas; los fondos decorativos se rasterizan una vez y se reutilizan; la pantalla que queda atrás se oscurece con una capa negra y no con un filtro, que obligaría a pintarla aparte en cada cuadro; y solo entra animado lo que se ve al abrir. Las mediciones están en el documento de la fase 9.

## Verificación

- [`test/core/movimiento_test.dart`](../../techstore-mobile/test/core/movimiento_test.dart): con «Quitar animaciones», las entradas aparecen enteras en el primer cuadro, el texto ya está escrito, el indicador de carga se queda quieto y el paso entre pantallas es solo un fundido. Sin restricciones, todas las entradas terminan solas y el latido de carga muere con la carga.
- [`test/core/vuelo_al_carrito_test.dart`](../../techstore-mobile/test/core/vuelo_al_carrito_test.dart): el vuelo llega al carrito y no se lanza con «Quitar animaciones».
- Búsqueda en el código: las únicas animaciones que se repiten (`repeat()`) son el arranque y el indicador de carga.
