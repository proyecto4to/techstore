import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/app/push_coordinator.dart';
import 'package:techstore_mobile/core/native/document_service.dart';

void main() {
  test('normaliza nombres de documentos antes de escribir en cache', () {
    expect(
      DocumentService.sanitizeFilename('../../factura 001?.pdf'),
      '.._.._factura_001_.pdf',
    );
  });

  test('convierte deep links push solo a rutas internas conocidas', () {
    expect(pushRoute('techstore://pedido/91'), '/orders/91');
    expect(pushRoute('techstore://factura/7'), '/invoices/7');
    expect(pushRoute('https://malicioso.test/pedido/91'), isNull);
    expect(pushRoute('techstore://desconocido/91'), isNull);
  });
}
