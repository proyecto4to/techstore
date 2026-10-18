import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/format/money.dart';
import 'package:techstore_mobile/core/widgets/precio_con_promocion.dart';
import 'package:techstore_mobile/features/catalog/domain/product.dart';

/// El precio con promoción, desde el JSON hasta lo que se dibuja.
///
/// La regla que sostiene todo esto: el número grande es siempre lo que se va a
/// cobrar. El precio de lista solo existe para tacharlo al lado.
void main() {
  group('Product', () {
    test('lee el precio ya descontado y la promoción que lo explica', () {
      final producto = Product.fromJson({
        'id': 1,
        'nombre': 'AMD Ryzen 9 7950X',
        'precio': 3360000,
        'precioLista': 4200000,
        'moneda': 'PYG',
        'disponible': true,
        'maxPorPedido': 3,
        'promocion': {
          'id': 9,
          'nombre': 'Liquidación',
          'porcentaje': 20,
          'hasta': '2026-09-30T23:59:00',
        },
      });

      expect(producto.price, 3360000);
      expect(producto.listPrice, 4200000);
      expect(producto.promotionPercentage, 20);
      expect(producto.promotionName, 'Liquidación');
      expect(producto.hasPromotion, isTrue);
    });

    test('un producto sin promoción no promete ninguna rebaja', () {
      final producto = Product.fromJson({
        'id': 1,
        'nombre': 'Sin oferta',
        'precio': 100000,
        'moneda': 'PYG',
        'disponible': true,
      });

      expect(producto.hasPromotion, isFalse);
      expect(producto.listPrice, isNull);
    });

    test('si el redondeo dejó el mismo precio, no hay nada que tachar', () {
      final producto = Product.fromJson({
        'id': 1,
        'nombre': 'Barato',
        'precio': 1,
        'precioLista': 1,
        'moneda': 'PYG',
        'disponible': true,
        'promocion': {'nombre': 'Casi nada', 'porcentaje': 20},
      });

      expect(producto.hasPromotion, isFalse);
    });

    test('la promoción sobrevive al carrito guardado en el teléfono', () {
      final original = Product.fromJson({
        'id': 1,
        'nombre': 'AMD',
        'precio': 80000,
        'precioLista': 100000,
        'moneda': 'PYG',
        'disponible': true,
        'promocion': {'nombre': 'Liquidación', 'porcentaje': 20},
      });

      final restaurado = Product.fromJson(original.toJson());

      expect(restaurado.price, 80000);
      expect(restaurado.listPrice, 100000);
      expect(restaurado.hasPromotion, isTrue);
      expect(restaurado.copyWith(maxPerOrder: 2).hasPromotion, isTrue);
    });
  });

  group('PrecioConPromocion', () {
    testWidgets('muestra el precio a cobrar y tacha el de lista', (
      tester,
    ) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: PrecioConPromocion(
              precio: 80000,
              moneda: 'PYG',
              precioLista: 100000,
              porcentaje: 20,
            ),
          ),
        ),
      );

      expect(find.text(formatMoney(80000, 'PYG')), findsOne);
      expect(find.text('-20%'), findsOne);

      final tachado = tester.widget<Text>(
        find.text(formatMoney(100000, 'PYG')),
      );
      expect(
        tachado.style?.decoration,
        TextDecoration.lineThrough,
        reason: 'el precio viejo solo puede aparecer tachado',
      );
    });

    testWidgets('sin promoción dibuja un único precio', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: PrecioConPromocion(precio: 100000, moneda: 'PYG'),
          ),
        ),
      );

      expect(find.text(formatMoney(100000, 'PYG')), findsOne);
      expect(find.byType(Text), findsOne);
    });
  });
}
