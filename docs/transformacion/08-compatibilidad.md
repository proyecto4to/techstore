# Fase 8 — Compatibilidad con Android, Redmi y HyperOS

Fecha: 2026-10-19. Rama: `rediseno-monokai`.

## Alcance

| | Valor |
|---|---|
| Android mínimo | 7.0 (SDK 24) |
| Android objetivo | SDK 36 (Android 16): rige el dibujo de borde a borde de Android 15 en adelante |
| Arquitecturas | APK para instalar a mano: `arm64-v8a` y `armeabi-v7a`. AAB: además `x86_64`, porque Play arma la descarga de cada equipo |
| Orientación | Vertical y horizontal; la app no la fija |
| Tema | Oscuro siempre, aunque el sistema esté en claro |

## Matriz de verificación

| Caso | Cómo se verificó | Estado |
|---|---|---|
| Android 15 (SDK 35), x86_64, 540×1200 a 210 dpi (411 dp) | Emulador QEMU con GPU del equipo: catálogo, ficha, carrito, ingreso, registro, sin conexión | Verificado |
| Borde a borde: barra de estado y barra de gestos | Emulador: el contenido respeta las dos barras; la ficha ubica el producto debajo de la barra flotante | Verificado (se corrigió la ficha) |
| Horizontal con recorte de cámara | Emulador girado: el catálogo respeta el recorte y no se desborda | Verificado |
| Pantalla angosta (346 dp, Moto G42 con pantalla agrandada) | Banco de capturas: vidriera y catálogo | Verificado |
| Redmi Note (393 dp) con la letra del sistema al 130 % | Banco de capturas: vidriera, catálogo, ficha, carrito, ingreso, registro, recuperación, cuenta, pedidos, notificaciones e inicio del personal | Verificado después de corregir cuatro desbordes (abajo) |
| Sin conexión con datos guardados | Emulador con wifi y datos apagados | Verificado |
| Teléfono real con Android 12–14 | Pasadas de septiembre en el Moto G42 (antes del rediseño) | **Pendiente de validación** del rediseño en el teléfono |
| Redmi o Poco con HyperOS | No hay un equipo disponible | **Pendiente de validación** (lista de comprobación abajo) |
| Tablet | Sin equipo; el diseño es fluido pero no se probó | **Pendiente de validación** |

## Lo que se ajustó para Redmi y HyperOS

- **Frecuencia de pantalla.** Los Redmi y Poco de 90 y 120 Hz dejan a cada app en 60 Hz salvo que la app pida más. `MainActivity` pide el modo más rápido que tenga la misma resolución; Flutter dibuja al ritmo de la pantalla. El emulador tiene un solo modo de 60 Hz, así que el efecto se comprueba en un teléfono.
- **Oscurecido forzado.** HyperOS y MIUI oscurecen por su cuenta las apps que no lo prohíben, y pueden alterar colores que ya son oscuros. `android:forceDarkAllowed=false` estaba solo en el tema de arranque; ahora también en el que usa la app mientras corre.
- **Letra agrandada.** HyperOS ofrece tamaños de letra grandes y es común encontrarlos subidos. Se agregaron al banco de capturas once pantallas a 393 dp con la letra al 130 %, y aparecieron cuatro defectos, ya corregidos: el carril de accesos de la vidriera partía «Notificaciones» al medio y el nombre se salía de la baldosa; el recuadro «Todo el catálogo» tenía alto fijo y el texto se salía por abajo; las baldosas del inicio del personal se desbordaban 2 píxeles; y en el total del carrito el «₲» saltaba a otra línea. Las alturas ahora crecen con la escala de la letra.
- **Recorte de cámara.** `windowLayoutInDisplayCutoutMode=shortEdges` en el arranque y áreas seguras en Flutter.

## Lista de comprobación en un Redmi con HyperOS

1. Instalar el APK. HyperOS pide permiso para instalar desde el explorador o el navegador y puede correr su análisis de seguridad: aceptar.
2. Abrir la app y comprobar que el arranque es negro, sin destello blanco, con el isotipo centrado.
3. En Ajustes → Pantalla, con la frecuencia en 120 Hz o «Dinámica», desplazar el catálogo: debe sentirse a la frecuencia alta. En Opciones de desarrollador, «Mostrar frecuencia de actualización» lo confirma.
4. Activar el modo oscuro del sistema y el oscurecido de apps de HyperOS: los colores de la app no deben cambiar.
5. Subir la letra del sistema al máximo y recorrer vidriera, catálogo, ficha, carrito y checkout: nada cortado ni encimado.
6. Con TalkBack: el carrito se anuncia como «Carrito, N unidades» y los avisos con acción esperan a que se los use.
7. Notificaciones (cuando Firebase esté configurado): en Ajustes → Apps → TechStore, habilitar «Inicio automático» y quitar el ahorro de batería; HyperOS corta las apps en segundo plano y sin eso el push puede llegar tarde.
8. Biometría: el sensor bajo la pantalla funciona con el diálogo del sistema (la app usa `BiometricPrompt` a través de `local_auth`).
9. Actualización desde la app: HyperOS pide habilitar «Instalar apps desconocidas» para TechStore; la app abre esa pantalla del sistema.
