# FASE 09 — CAPACIDADES NATIVAS

ESTADO: COMPLETED

## Implementado

- Cámara y galería Android para productos y comprobantes de compra.
- Recorte, conversión JPEG, compresión a 82 %, límite 1600×1600 y máximo 5 MB.
- Archivo privado de comprobante centralizado en backend, validado por firma JPG/PNG/PDF y disponible en Web y Mobile.
- Biometría Android para desbloquear la aplicación; solo se guarda la preferencia, nunca datos biométricos.
- Firebase Cloud Messaging opcional por flavor mediante `dart-define`, registro/rotación/desactivación de dispositivo y proveedor FCM backend con Application Default Credentials.
- Deep links `techstore://pedido/{id}`, `techstore://factura` y `techstore://notificaciones`.
- Apertura, descarga autenticada, compartir e impresión de facturas, KuDE, XML, notas de crédito, libros IVA y reportes.
- Centro Mobile de exportación ZIP del Registro de Comprobantes DNIT.

## Decisiones por utilidad real

- QR/barcode: no se incorpora; el dominio de productos no posee SKU/EAN/código de barras y escanear no permitiría resolver una entidad real. Agregarlo sin modelo de negocio crearía un flujo ficticio.
- GPS: no es necesario; el checkout usa direcciones y tarifas explícitas, sin seguimiento geográfico.
- Bluetooth/NFC: no existe periférico ni operación del negocio que los requiera.
- La entrega FCM real se verificará con credenciales/proyecto Firebase y dispositivo físico en Fase 16; la implementación se prueba aquí mediante proveedor mock y build Android.

## Validación

- Backend dirigido: 19/19 tests aprobados (FCM + comprobantes/archivo).
- Flutter: `analyze` sin incidencias y 14/14 tests aprobados.
- Web: lint, 14/14 tests y build de producción aprobados.
- Backend: 326 unitarias aprobadas; la suite de 43 integraciones solo detectó el contador V44→V45, corregido y revalidado con 18/18 pruebas de migración.
- OpenAPI 3.1: 101 rutas y 124 operaciones válidas.
- APK Development debug con plugins nativos compilado correctamente.
- Stack local actualizado: Web `200`, backend `UP`; comprobantes privados sin autenticación `401` e imagen pública inexistente `404`.
