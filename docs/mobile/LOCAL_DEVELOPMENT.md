# Desarrollo local de TechStore Mobile

## Ciclo diario recomendado

```powershell
cd "C:\Users\Dell Vostro\OneDrive\Documentos\GitHub\techstore\techstore-mobile"
npm ci
npm run check
npx expo start --dev-client
```

`npm ci` solo es necesario al instalar el proyecto o cuando cambia `package-lock.json`. Durante el trabajo normal, Metro aplica actualización rápida al guardar.

Para un repaso visual inmediato sin dispositivo:

```powershell
npm run web
```

Para la revisión nativa usá el development build instalado en Android. Expo Go queda limitado a una vista provisional.

## Qué se puede probar hoy

En Fase 3 se debe comprobar:

1. La entrada redirige a las cinco pestañas principales.
2. `Inicio` carga marcas/productos reales o indica claramente que la API no está configurada.
3. `Buscar` filtra por texto, marca, disponibilidad y orden; `Carrito` y `Pedidos` mantienen sus estados base.
4. `Cuenta` alterna sistema/oscuro/claro durante la sesión actual.
5. `Iniciar sesión` y `Crear cuenta` validan formularios y consumen la API configurada.
6. El detalle `/product/[id]` carga solo productos públicos visibles y permite guardar/quitar favoritos.
7. Al cerrar sesión se borra el refresh local aunque el servidor no esté disponible.

El catálogo y los favoritos sí provienen del backend cuando la URL está configurada. Carrito, pedidos y timeline continúan como shells hasta sus fases; usá únicamente cuentas de un entorno de desarrollo controlado.

## Controles antes de entregar un cambio

```powershell
npm run check
npm run doctor
npm run build:web
```

Detalle:

- `npm run typecheck`: TypeScript estricto sin emisión.
- `npm run lint`: Expo ESLint, máximo de warnings permitido `0`.
- `npm test`: Jest con preset `jest-expo` y ejecución en serie.
- `npm run check`: combina los tres controles anteriores.
- `npm run doctor`: verifica versiones y configuración Expo.
- `npm run build:web`: genera todas las rutas estáticas en `dist/`.

Usá `npm run test:watch` mientras desarrollás componentes o tokens.

## Organización de cambios

- Agregá una ruta delgada en `src/app` y ubicá la pantalla en su carpeta `src/features/<dominio>`.
- Reutilizá tokens de `src/theme`; no dupliques colores, radios o espaciados en features.
- Promové un patrón a `src/components/ui` solo cuando sea reutilizable y ajeno al negocio.
- Mantené el estado remoto futuro en TanStack Query y el estado local global necesario en Zustand.
- No realices llamadas HTTP ni lecturas de SecureStore directamente desde componentes visuales.
- Agregá pruebas en `tests/` para contratos estables de tema/componentes y, en fases siguientes, para servicios y stores.

## Android físico y emulador

Con development build ya instalado:

```powershell
npm run android
```

Si Metro no aparece en un teléfono físico:

1. Confirmá que teléfono y PC están en la misma red.
2. Permití Node/Expo en el firewall solo para la red privada de desarrollo.
3. Probá explícitamente el modo development client: `npx expo start --dev-client`.
4. Como alternativa temporal de red, usá `npx expo start --dev-client --tunnel`; requiere Internet y es más lento.

Si cambió `app.config.ts`, un plugin Expo o una dependencia nativa, reiniciar Metro no alcanza: recompilá el development build.

## Problemas frecuentes

### Metro conserva código o variables antiguas

```powershell
npx expo start --clear
```

### El teléfono no puede alcanzar el backend

No uses `localhost` en `EXPO_PUBLIC_API_URL` desde un dispositivo físico. Indicá la IP LAN, por ejemplo `http://192.168.1.50:8090/api/v1`, y verificá que el backend escuche fuera de loopback. En el emulador Android estándar se usa `http://10.0.2.2:8090/api/v1`.

Reiniciá Metro después de cambiar esa URL. En producción la configuración rechaza HTTP, `localhost`, `127.0.0.1` y `10.0.2.2`.

### Expo Go abre pero una capacidad nativa no coincide

Repetí la prueba en el development build. Expo Go no incorpora necesariamente la configuración nativa declarada por este proyecto.

### La auditoría propone `npm audit fix --force`

No lo ejecutes. Los avisos actuales son transitivos del tooling y no tienen parche compatible directo en Expo SDK 57; la corrección forzada altera el árbol a versiones incompatibles. Revisá primero una actualización soportada por Expo y ejecutá luego:

```powershell
npx expo install --check
npm run doctor
npm run check
```

## Reinicio limpio seguro

Para reconstruir únicamente artefactos generados:

```powershell
npm ci
npx expo start --clear
```

No uses el script de reset del template ni elimines archivos de features para resolver problemas de caché.
