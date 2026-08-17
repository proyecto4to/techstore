# Pruebas E2E móviles

TechStore Mobile prepara sus recorridos críticos con Maestro sobre el paquete nativo `com.techstore.mobile`. Los flujos usan la accessibility tree y `testID` estables; no dependen de coordenadas de pantalla.

## Flujos

- `flow-a-purchase.yaml`: registro, logout/login, catálogo, producto, carrito, dirección, entrega, pago, checkout idempotente y tracking.
- `flow-b-chat-recovery.yaml`: login, pedido, conversación asociada, mensaje, muerte del proceso, recuperación REST y reconexión WebSocket.
- `manual/flow-b-admin-reply.yaml`: recepción real de una respuesta enviada desde Admin Web; se mantiene fuera de la suite automática porque necesita coordinación de un segundo actor.

## Datos requeridos

No se guardan credenciales en YAML. Antes de ejecutar se definen variables de entorno efímeras:

```text
E2E_EMAIL=
E2E_PASSWORD=
E2E_PRODUCT_QUERY=
E2E_PRODUCT_ID=
E2E_SHIPPING_LABEL=
E2E_CHAT_EMAIL=
E2E_CHAT_PASSWORD=
E2E_ORDER_NUMBER=
E2E_INITIAL_MESSAGE=
E2E_FOLLOWUP_MESSAGE=
E2E_CONVERSATION_ID=
E2E_ADMIN_REPLY=
```

`E2E_EMAIL` debe ser una cuenta descartable todavía no registrada. La cuenta de chat debe tener un pedido consultable y el catálogo debe contener el producto indicado, con stock.

## Ejecución

Requisitos externos: APK/Development Build instalado, backend accesible en `8090`, dispositivo o emulador conectado y Maestro Studio/CLI disponible.

```bash
cd techstore-mobile
npm run e2e:maestro
```

Para la respuesta de Admin Web:

```bash
npm run e2e:maestro:manual-chat
```

Durante el segundo flujo, un operador o automatización web debe enviar exactamente `E2E_ADMIN_REPLY` dentro de 60 segundos.

## Estado local de Fase 13

Los YAML y selectores se validan estáticamente en el repositorio. La ejecución end-to-end real requiere el APK de Fase 15 y un backend con fixtures controlados; no se simula un resultado verde sin esos actores.

Referencias: [Maestro QuickStart](https://docs.maestro.dev/getting-started/writing-your-first-flow), [runFlow](https://docs.maestro.dev/api-reference/commands/runflow) y [selectores](https://docs.maestro.dev/api-reference/selectors).
