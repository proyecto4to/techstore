import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';

Future<void> main() async {
  final baseUrl =
      Platform.environment['TECHSTORE_E2E_BASE_URL'] ?? 'http://127.0.0.1:8092';
  final adminEmail = Platform.environment['TECHSTORE_E2E_ADMIN_EMAIL'];
  final adminPassword = Platform.environment['TECHSTORE_E2E_ADMIN_PASSWORD'];
  if (adminEmail == null || adminPassword == null) {
    stderr.writeln(
      'Defini TECHSTORE_E2E_ADMIN_EMAIL y TECHSTORE_E2E_ADMIN_PASSWORD.',
    );
    exitCode = 2;
    return;
  }

  final anonymous = Dio(BaseOptions(baseUrl: baseUrl));
  final adminLogin = _map(
    await anonymous.post<dynamic>(
      '/api/v1/auth/login',
      data: {'email': adminEmail, 'password': adminPassword},
    ),
  );
  final web = _authenticated(baseUrl, adminLogin['accessToken'] as String);
  final runId = DateTime.now().toUtc().millisecondsSinceEpoch.toString();
  final numericRunId = int.parse(runId);
  final pointCode = ((numericRunId % 900) + 100).toString();
  final rucSuffix = (numericRunId % 10000000).toString().padLeft(7, '0');
  final rucNumber = '8$rucSuffix';
  final supplierRuc = '$rucNumber-${_rucDv(rucNumber)}';
  final receiptNumber = '001-001-$rucSuffix';
  final mobileAdminEmail = 'phase20.mobile.admin.$runId@example.invalid';
  const initialMobilePassword = 'Phase20Mobile9!';
  const rotatedMobilePassword = 'Phase20Mobile8!';
  final clientEmail = 'phase20.client.$runId@example.invalid';
  const clientPassword = 'Phase20Client9!';
  final completed = <String>[];

  void done(String name) {
    completed.add(name);
    stdout.writeln('PHASE20_DOMAIN_OK $name');
  }

  // Usuarios y permisos.
  final mobileUser = _map(
    await web.post<dynamic>(
      '/api/v1/usuarios',
      data: {
        'nombre': 'Mobile',
        'apellido': 'Operator',
        'email': mobileAdminEmail,
        'password': initialMobilePassword,
        'rol': 'Admin',
      },
    ),
  );
  final mobileUserId = _id(mobileUser);
  await web.put<dynamic>(
    '/api/v1/usuarios/$mobileUserId/activo',
    data: {'activo': false},
  );
  await _expectStatus(
    () => anonymous.post<dynamic>(
      '/api/v1/auth/login',
      data: {'email': mobileAdminEmail, 'password': initialMobilePassword},
    ),
    403,
  );
  await web.put<dynamic>(
    '/api/v1/usuarios/$mobileUserId/activo',
    data: {'activo': true},
  );
  await web.put<dynamic>(
    '/api/v1/usuarios/$mobileUserId/password',
    data: {'password': rotatedMobilePassword},
  );
  final mobileLogin = _map(
    await anonymous.post<dynamic>(
      '/api/v1/auth/login',
      data: {'email': mobileAdminEmail, 'password': rotatedMobilePassword},
    ),
  );
  final mobile = _authenticated(baseUrl, mobileLogin['accessToken'] as String);
  final users = _content(
    await web.get<dynamic>(
      '/api/v1/usuarios',
      queryParameters: {'page': 0, 'size': 200},
    ),
  );
  _check(users.any((item) => _id(_asMap(item)) == mobileUserId), 'usuario');
  done('users_permissions_lifecycle');

  // Empresa y puntos de expedicion.
  final company = _map(await web.get<dynamic>('/api/v1/empresa'));
  final companyUpdate = Map<String, dynamic>.from(company)
    ..['telefono'] = '(021) 555-P20';
  final updatedCompany = _map(
    await web.put<dynamic>('/api/v1/empresa', data: companyUpdate),
  );
  _check(updatedCompany['telefono'] == '(021) 555-P20', 'empresa');
  final issuingPoint = _map(
    await web.post<dynamic>(
      '/api/v1/empresa/puntos-expedicion',
      data: {
        'establecimiento': '001',
        'punto': pointCode,
        'nombre': 'Phase 20 Mobile',
        'activo': true,
      },
    ),
  );
  final issuingPointId = _id(issuingPoint);
  await web.put<dynamic>(
    '/api/v1/usuarios/$mobileUserId/punto-expedicion',
    data: {'puntoExpedicionId': issuingPointId},
  );
  await web.put<dynamic>(
    '/api/v1/empresa/puntos-expedicion/$issuingPointId/activo',
    data: {'activo': false},
  );
  await web.put<dynamic>(
    '/api/v1/empresa/puntos-expedicion/$issuingPointId/activo',
    data: {'activo': true},
  );
  done('company_issuing_points');

  // Producto completo y baja fisica de una entidad aun no relacionada.
  final brands = _list(await web.get<dynamic>('/api/v1/marcas'));
  _check(brands.isNotEmpty, 'marcas');
  final brandId = _id(_asMap(brands.first));
  final productBody = <String, dynamic>{
    'nombre': 'Phase 20 Product $runId',
    'descripcion': 'Producto descartable de integracion total',
    'precio': 120000,
    'iva': 10,
    'stockReal': 20,
    'stockMinimo': 4,
    'pesoKg': 1.25,
    'marcaId': brandId,
    'modeloId': null,
    'imagenUrl': null,
    'activo': true,
    'ocultarSinStock': false,
  };
  final product = _map(
    await web.post<dynamic>('/api/v1/productos', data: productBody),
  );
  final productId = _id(product);
  final readProduct = _map(
    await mobile.get<dynamic>('/api/v1/productos/admin/$productId'),
  );
  _check((readProduct['stockReal'] as num).toInt() == 20, 'producto alta');
  final updatedProduct = _map(
    await mobile.put<dynamic>(
      '/api/v1/productos/$productId',
      data: {
        ...productBody,
        'descripcion': 'Actualizado desde Mobile',
        'stockReal': 22,
      },
    ),
  );
  _check(
    updatedProduct['descripcion'] == 'Actualizado desde Mobile',
    'producto update',
  );
  final productImage = _map(
    await mobile.post<dynamic>(
      '/api/v1/productos/$productId/imagen',
      data: FormData.fromMap({
        'archivo': MultipartFile.fromBytes(
          base64Decode(
            'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9WlX9p8AAAAASUVORK5CYII=',
          ),
          filename: 'phase20.png',
          contentType: DioMediaType('image', 'png'),
        ),
      }),
    ),
  );
  _check(
    (productImage['imagenUrl'] as String).contains('/uploads/'),
    'upload imagen producto',
  );
  final disposable = _map(
    await web.post<dynamic>(
      '/api/v1/productos',
      data: {...productBody, 'nombre': 'Phase 20 Delete $runId'},
    ),
  );
  final disposableId = _id(disposable);
  await web.delete<dynamic>('/api/v1/productos/$disposableId');
  final deletedAdminView = _map(
    await web.get<dynamic>('/api/v1/productos/admin/$disposableId'),
  );
  _check(deletedAdminView['activo'] == false, 'producto baja logica');
  await _expectStatus(
    () => web.get<dynamic>('/api/v1/productos/$disposableId'),
    404,
  );
  done('products_crud_stock');

  // Proveedores, ofertas, comparador y compras.
  final supplierBody = <String, dynamic>{
    'razonSocial': 'Phase 20 Supplier $runId',
    'nombreFantasia': 'P20 Supplier',
    'email': 'supplier.$runId@example.invalid',
    'telefono': '0981123456',
    'direccion': 'Calle Integration 20',
    'ciudad': 'Asuncion',
    'pais': 'Paraguay',
    'ruc': supplierRuc,
  };
  final supplier = _map(
    await mobile.post<dynamic>('/api/v1/proveedores', data: supplierBody),
  );
  final supplierId = _id(supplier);
  final updatedSupplier = _map(
    await web.put<dynamic>(
      '/api/v1/proveedores/$supplierId',
      data: {...supplierBody, 'nombreFantasia': 'P20 Updated'},
    ),
  );
  _check(
    updatedSupplier['nombreFantasia'] == 'P20 Updated',
    'proveedor update',
  );
  await web.put<dynamic>(
    '/api/v1/proveedores/$supplierId/activo',
    data: {'activo': false},
  );
  await web.put<dynamic>(
    '/api/v1/proveedores/$supplierId/activo',
    data: {'activo': true},
  );
  await mobile.put<dynamic>(
    '/api/v1/proveedores/$supplierId/ofertas',
    data: {'productoId': productId, 'precio': 50000, 'disponible': true},
  );
  final offers = _list(
    await web.get<dynamic>('/api/v1/proveedores/$supplierId/ofertas'),
  );
  _check(
    offers.any((item) => _asMap(item)['productoId'] == productId),
    'oferta',
  );
  final comparison = _list(
    await web.post<dynamic>(
      '/api/v1/compras/comparar',
      data: {
        'items': [
          {'productoId': productId, 'cantidad': 2},
        ],
      },
    ),
  );
  _check(comparison.isNotEmpty, 'comparador');
  done('suppliers_offers_comparison');

  // Tarifas y cotizacion de entrega.
  final rateBody = <String, dynamic>{
    'codigo': 'P20-$runId',
    'nombre': 'Entrega Phase 20',
    'descripcion': 'Tarifa descartable',
    'tipo': 'ENTREGA_LOCAL',
    'providerCode': 'INTERNAL-P20',
    'transportista': 'TechStore',
    'departamento': 'Central',
    'ciudad': 'Asuncion',
    'costo': 25000,
    'iva': 10,
    'diasEstimadosMin': 1,
    'diasEstimadosMax': 2,
    'prioridad': 1,
    'promocion': false,
    'activa': true,
  };
  final rate = _map(
    await web.post<dynamic>('/api/v1/envios/tarifas', data: rateBody),
  );
  final rateId = _id(rate);
  final updatedRate = _map(
    await web.put<dynamic>(
      '/api/v1/envios/tarifas/$rateId',
      data: {...rateBody, 'costo': 30000},
    ),
  );
  _check((updatedRate['costo'] as num).toInt() == 30000, 'tarifa update');
  done('shipping_rates');

  // Cliente real: direcciones, favoritos, carrito, cotizacion y push.
  final registration = _map(
    await anonymous.post<dynamic>(
      '/api/v1/auth/registro',
      data: {
        'nombre': 'Phase',
        'apellido': 'Client',
        'email': clientEmail,
        'password': clientPassword,
      },
    ),
  );
  final client = _authenticated(baseUrl, registration['accessToken'] as String);
  final addressBody = <String, dynamic>{
    'nombreDestinatario': 'Phase 20 Client',
    'telefono': '0981111111',
    'departamento': 'Central',
    'ciudad': 'Asuncion',
    'barrio': 'Centro',
    'direccionLinea1': 'Calle Phase 20',
    'direccionLinea2': null,
    'numeroCasa': '20',
    'referencia': 'Integracion total',
    'codigoPostal': '1209',
    'latitud': -25.2867,
    'longitud': -57.3333,
    'principal': true,
  };
  final address = _map(
    await client.post<dynamic>('/api/v1/direcciones', data: addressBody),
  );
  final addressId = _id(address);
  final changedAddress = _map(
    await client.put<dynamic>(
      '/api/v1/direcciones/$addressId',
      data: {...addressBody, 'direccionLinea1': 'Calle Phase 21'},
    ),
  );
  _check(changedAddress['direccionLinea1'] == 'Calle Phase 21', 'direccion');
  await client.post<dynamic>('/api/v1/favoritos/$productId');
  final favorites = _list(await client.get<dynamic>('/api/v1/favoritos'));
  _check(favorites.isNotEmpty, 'favoritos');
  await client.post<dynamic>(
    '/api/v1/carrito/validar',
    data: {
      'items': [
        {'productoId': productId, 'cantidad': 2},
      ],
    },
  );
  final quotes = _list(
    await client.post<dynamic>(
      '/api/v1/envios/cotizar',
      data: {
        'direccionEntregaId': addressId,
        'items': [
          {'productoId': productId, 'cantidad': 1},
        ],
      },
    ),
  );
  _check(quotes.isNotEmpty, 'cotizacion envio');
  const deviceId = 'phase20.android.device.0001';
  await client.put<dynamic>(
    '/api/v1/dispositivos/actual',
    data: {
      'plataforma': 'ANDROID',
      'pushToken': 'phase20_fake_push_token_1234567890',
      'deviceIdentifier': deviceId,
    },
  );
  final devices = _list(await client.get<dynamic>('/api/v1/dispositivos'));
  _check(
    devices.any((item) => _asMap(item)['deviceIdentifier'] == deviceId),
    'dispositivo',
  );
  await client.delete<dynamic>('/api/v1/dispositivos/actual/$deviceId');
  done('client_address_favorites_cart_shipping_push');

  // Mensajeria REST e idempotencia de clientMessageId.
  final conversation = _map(
    await client.post<dynamic>(
      '/api/v1/conversaciones',
      data: {
        'pedidoId': null,
        'clientMessageId': 'p20-conversation-$runId',
        'mensaje': 'Inicio de conversacion Phase 20',
      },
    ),
  );
  final conversationId = _id(_asMap(conversation['conversacion']));
  final messageBody = {
    'clientMessageId': 'p20-message-$runId',
    'contenido': 'Mensaje offline idempotente',
  };
  final firstMessage = _map(
    await client.post<dynamic>(
      '/api/v1/conversaciones/$conversationId/mensajes',
      data: messageBody,
    ),
  );
  final replayedMessage = _map(
    await client.post<dynamic>(
      '/api/v1/conversaciones/$conversationId/mensajes',
      data: messageBody,
    ),
  );
  _check(_id(firstMessage) == _id(replayedMessage), 'mensaje idempotente');
  await client.get<dynamic>('/api/v1/conversaciones/$conversationId/mensajes');
  await client.put<dynamic>('/api/v1/conversaciones/$conversationId/leido');
  await client.put<dynamic>('/api/v1/conversaciones/$conversationId/cerrar');
  done('messaging_idempotency');

  // Checkout idempotente, compromiso y restauracion de stock.
  final checkoutBody = {
    'items': [
      {'productoId': productId, 'cantidad': 2},
    ],
    'notas': 'Phase 20 idempotent checkout',
    'metodoEntrega': 'RETIRO_TIENDA',
    'metodoPago': 'PAGO_EN_LOCAL',
  };
  final idempotencyKey = 'phase20-checkout-$runId';
  final firstOrder = _map(
    await client.post<dynamic>(
      '/api/v1/pedidos',
      data: checkoutBody,
      options: Options(headers: {'Idempotency-Key': idempotencyKey}),
    ),
  );
  final replayedOrder = _map(
    await client.post<dynamic>(
      '/api/v1/pedidos',
      data: checkoutBody,
      options: Options(headers: {'Idempotency-Key': idempotencyKey}),
    ),
  );
  final firstOrderId = _id(firstOrder);
  _check(firstOrderId == _id(replayedOrder), 'checkout idempotente');
  final reserved = _map(
    await web.get<dynamic>('/api/v1/productos/admin/$productId'),
  );
  _check(
    (reserved['stockComprometido'] as num).toInt() == 2,
    'stock comprometido',
  );
  await client.put<dynamic>('/api/v1/pedidos/$firstOrderId/cancelar');
  final restored = _map(
    await web.get<dynamic>('/api/v1/productos/admin/$productId'),
  );
  _check(
    (restored['stockComprometido'] as num).toInt() == 0,
    'stock restaurado',
  );
  done('orders_idempotency_stock_restore');

  // Caja, venta directa, factura, PDF y reportes.
  var opening = _map(await web.get<dynamic>('/api/v1/caja/apertura'));
  if (opening['abierta'] != true) {
    opening = _map(
      await web.post<dynamic>(
        '/api/v1/caja/apertura',
        data: {'saldoInicial': 1000000, 'notas': 'Phase 20'},
      ),
    );
  }
  _check(opening['abierta'] == true, 'apertura caja');
  await web.post<dynamic>(
    '/api/v1/caja/movimientos',
    data: {
      'tipo': 'Ingreso',
      'monto': 10000,
      'concepto': 'Ingreso Phase 20',
      'referencia': 'P20-$runId',
      'medio': 'Efectivo',
    },
  );
  final directSale = _map(
    await web.post<dynamic>(
      '/api/v1/ventas',
      data: {
        'items': [
          {'productoId': productId, 'cantidad': 1, 'descuento': 0},
        ],
        'lineas': [
          {'tipoPago': 'Efectivo', 'monto': 120000},
        ],
        'receptor': null,
        'notas': 'Phase 20 direct sale',
      },
    ),
  );
  _check((directSale['total'] as num).toInt() == 120000, 'venta directa');
  final invoice = _asMap(directSale['factura']);
  final invoiceId = _id(invoice);
  await _expectPdf(web, '/api/v1/facturas/$invoiceId/pdf');
  await web.get<dynamic>('/api/v1/facturas/$invoiceId');
  await web.get<dynamic>('/api/v1/reportes/ventas');
  await _expectPdf(web, '/api/v1/reportes/ventas/pdf');
  await web.get<dynamic>('/api/v1/reportes/stock-bajo');
  await _expectPdf(web, '/api/v1/reportes/stock-bajo/pdf');
  done('cash_direct_sale_invoice_reports');

  // Compra: alta, recepcion de stock, comprobante privado y pago.
  final stockBeforePurchase =
      (_map(
                await web.get<dynamic>('/api/v1/productos/admin/$productId'),
              )['stockReal']
              as num)
          .toInt();
  final purchase = _map(
    await mobile.post<dynamic>(
      '/api/v1/compras',
      data: {
        'proveedorId': supplierId,
        'items': [
          {'productoId': productId, 'cantidad': 2, 'costoUnitario': 50000},
        ],
        'notas': 'Phase 20 purchase',
      },
    ),
  );
  final purchaseId = _id(purchase);
  await mobile.put<dynamic>('/api/v1/compras/$purchaseId/recibir');
  final stockAfterPurchase =
      (_map(
                await web.get<dynamic>('/api/v1/productos/admin/$productId'),
              )['stockReal']
              as num)
          .toInt();
  _check(stockAfterPurchase == stockBeforePurchase + 2, 'recepcion stock');
  final receipt = _map(
    await mobile.put<dynamic>(
      '/api/v1/compras/$purchaseId/comprobante',
      data: {
        'tipoComprobante': 109,
        'timbrado': '87654321',
        'numero': receiptNumber,
        'fechaEmision': DateTime.now().toIso8601String().substring(0, 10),
        'condicion': 'Contado',
      },
    ),
  );
  _check(receipt['numero'] == receiptNumber, 'comprobante compra');
  final pdfBytes = utf8.encode(
    '%PDF-1.4\n% Phase 20 integration artifact\n%%EOF\n',
  );
  final uploadedReceipt = _map(
    await mobile.post<dynamic>(
      '/api/v1/compras/$purchaseId/comprobante/archivo',
      data: FormData.fromMap({
        'archivo': MultipartFile.fromBytes(pdfBytes, filename: 'phase20.pdf'),
      }),
    ),
  );
  _check(uploadedReceipt['archivoDisponible'] == true, 'upload comprobante');
  final downloadedReceipt = await mobile.get<List<int>>(
    '/api/v1/compras/$purchaseId/comprobante/archivo',
    options: Options(responseType: ResponseType.bytes),
  );
  _check(
    (downloadedReceipt.data ?? const []).isNotEmpty,
    'download comprobante',
  );
  await mobile.put<dynamic>(
    '/api/v1/compras/$purchaseId/pagar',
    data: {
      'lineas': [
        {'tipoPago': 'Efectivo', 'monto': 100000},
      ],
    },
  );
  final purchases = _content(await web.get<dynamic>('/api/v1/compras'));
  _check(
    purchases.any((item) => _id(_asMap(item)) == purchaseId),
    'listar compras',
  );
  done('purchases_receiving_private_document_payment');

  // Contabilidad, dashboard, notificaciones, auditoria y recuperacion sync.
  final today = DateTime.now().toIso8601String().substring(0, 10);
  final fiscalPeriod = {'desde': today, 'hasta': today};
  await web.get<dynamic>('/api/v1/dashboard');
  await web.get<dynamic>(
    '/api/v1/contabilidad/resumen-iva',
    queryParameters: fiscalPeriod,
  );
  await web.get<dynamic>(
    '/api/v1/contabilidad/libro-ventas',
    queryParameters: fiscalPeriod,
  );
  await web.get<dynamic>(
    '/api/v1/contabilidad/libro-compras',
    queryParameters: fiscalPeriod,
  );
  await _expectPdf(
    web,
    '/api/v1/contabilidad/resumen-iva/pdf',
    queryParameters: fiscalPeriod,
  );
  await _expectPdf(
    web,
    '/api/v1/contabilidad/libro-ventas/pdf',
    queryParameters: fiscalPeriod,
  );
  await _expectPdf(
    web,
    '/api/v1/contabilidad/libro-compras/pdf',
    queryParameters: fiscalPeriod,
  );
  await web.get<dynamic>(
    '/api/v1/contabilidad/registro-comprobantes',
    queryParameters: {'anio': DateTime.now().year, 'mes': DateTime.now().month},
  );
  await web.get<dynamic>('/api/v1/notificaciones');
  await web.get<dynamic>('/api/v1/notificaciones/no-leidas');
  await web.put<dynamic>('/api/v1/notificaciones/leidas');
  final audit = _content(await web.get<dynamic>('/api/v1/auditoria'));
  _check(audit.isNotEmpty, 'auditoria');
  await web.get<dynamic>('/api/v1/auditoria/entidades');
  final syncEvents = _list(
    await mobile.get<dynamic>(
      '/api/v1/sync/events',
      queryParameters: {'after': 0, 'limit': 200},
    ),
  );
  _check(syncEvents.isNotEmpty, 'sync recovery');
  final version = _map(
    await anonymous.get<dynamic>(
      '/api/v1/app/version',
      queryParameters: {'platform': 'android', 'versionCode': 1},
    ),
  );
  _check(version['platform'] == 'android', 'version policy');
  done('dashboard_accounting_notifications_audit_sync_version');

  // Cierre de caja y limpieza funcional del cliente.
  final preview = _map(await web.get<dynamic>('/api/v1/caja/cierres/preview'));
  final expectedCash = preview['efectivoTeorico'] as num;
  await web.post<dynamic>(
    '/api/v1/caja/cierres',
    data: {'efectivoContado': expectedCash, 'notas': 'Phase 20 close'},
  );
  await _expectPdf(web, '/api/v1/caja/resumen/pdf');
  await client.delete<dynamic>('/api/v1/favoritos/$productId');
  await client.delete<dynamic>('/api/v1/direcciones/$addressId');
  done('cash_close_and_client_cleanup');

  stdout.writeln(
    'INTEGRATION_TOTAL_E2E_OK domains=${completed.length} '
    'checks=${completed.join(',')}',
  );
}

Dio _authenticated(String baseUrl, String token) => Dio(
  BaseOptions(
    baseUrl: baseUrl,
    headers: {'Authorization': 'Bearer $token'},
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 30),
  ),
);

Map<String, dynamic> _map(Response<dynamic> response) => _asMap(response.data);

Map<String, dynamic> _asMap(dynamic value) {
  if (value is Map<String, dynamic>) return value;
  if (value is Map) return value.cast<String, dynamic>();
  throw StateError(
    'Se esperaba un objeto JSON y se recibio ${value.runtimeType}.',
  );
}

List<dynamic> _list(Response<dynamic> response) {
  final value = response.data;
  if (value is List) return value;
  throw StateError(
    'Se esperaba una lista JSON y se recibio ${value.runtimeType}.',
  );
}

List<dynamic> _content(Response<dynamic> response) {
  final value = _map(response)['content'];
  if (value is List) return value;
  throw StateError('La respuesta paginada no contiene content.');
}

int _id(Map<String, dynamic> value) => (value['id'] as num).toInt();

void _check(bool condition, String label) {
  if (!condition) throw StateError('Fallo de integracion: $label.');
}

Future<void> _expectStatus(
  Future<Response<dynamic>> Function() action,
  int expected,
) async {
  try {
    await action();
  } on DioException catch (error) {
    if (error.response?.statusCode == expected) return;
    rethrow;
  }
  throw StateError('Se esperaba HTTP $expected y la operacion fue exitosa.');
}

Future<void> _expectPdf(
  Dio api,
  String path, {
  Map<String, dynamic>? queryParameters,
}) async {
  final response = await api.get<List<int>>(
    path,
    queryParameters: queryParameters,
    options: Options(responseType: ResponseType.bytes),
  );
  final bytes = response.data ?? const [];
  _check(
    bytes.length > 4 && ascii.decode(bytes.take(4).toList()) == '%PDF',
    path,
  );
}

int _rucDv(String number) {
  var total = 0;
  var weight = 2;
  for (var index = number.length - 1; index >= 0; index--) {
    total += int.parse(number[index]) * weight;
    weight = weight == 11 ? 2 : weight + 1;
  }
  final remainder = total % 11;
  return remainder > 1 ? 11 - remainder : 0;
}
