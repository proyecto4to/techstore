import 'package:drift/native.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/app/environment.dart';
import 'package:techstore_mobile/core/cache/cache_store.dart';
import 'package:techstore_mobile/core/database/app_database.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/storage/secure_store.dart';
import 'package:techstore_mobile/features/cart/application/cart_controller.dart';
import 'package:techstore_mobile/features/catalog/domain/product.dart';

/// Reglas de stock del carrito: nunca puede quedar pidiendo más de lo que el
/// servidor acepta, ni siquiera sumando lo que ya estaba adentro.
void main() {
  late ProviderContainer container;

  setUp(() {
    final database = AppDatabase(NativeDatabase.memory());
    container = ProviderContainer(
      overrides: [
        cacheStoreProvider.overrideWithValue(CacheStore(database)),
        environmentProvider.overrideWithValue(AppEnvironment.development()),
        secureStoreProvider.overrideWithValue(_MemorySecureStore()),
      ],
    );
    addTearDown(container.dispose);
    addTearDown(database.close);
  });

  CartController cart() => container.read(cartProvider.notifier);

  Product product({int max = 10, int id = 1}) => Product(
    id: id,
    name: 'Notebook',
    price: 1000,
    currency: 'PYG',
    available: max > 0,
    maxPerOrder: max,
  );

  test('agregar de a uno respeta el tope informado por el servidor', () {
    final item = product(max: 3);

    expect(cart().add(item).outcome, CartOutcome.added);
    expect(cart().add(item).outcome, CartOutcome.added);
    expect(cart().add(item).outcome, CartOutcome.added);

    final cuarto = cart().add(item);
    expect(cuarto.outcome, CartOutcome.alreadyAtMax);
    expect(cart().quantityOf(item.id), 3);
  });

  test('pedir más de lo que queda entra hasta el tope y lo explica', () {
    final item = product(max: 5);

    final result = cart().add(item, quantity: 8);

    expect(result.outcome, CartOutcome.capped);
    expect(result.added, 5);
    expect(cart().quantityOf(item.id), 5);
    expect(result.message, contains('5 unidades'));
  });

  test('lo que ya está en el carrito descuenta del margen disponible', () {
    // El caso del enunciado: hay 10, ya tiene 7, intenta agregar 5.
    final item = product(max: 10);
    cart().add(item, quantity: 7);

    final result = cart().add(item, quantity: 5);

    expect(result.outcome, CartOutcome.capped);
    expect(result.added, 3);
    expect(cart().quantityOf(item.id), 10);
    expect(cart().roomFor(item), 0);
  });

  test('un producto sin unidades no entra al carrito', () {
    final result = cart().add(product(max: 0));

    expect(result.outcome, CartOutcome.unavailable);
    expect(container.read(cartProvider), isEmpty);
  });

  test('escribir una cantidad mayor al stock la recorta en vez de aceptarla', () {
    final item = product(max: 8);
    cart().add(item);

    final quedo = cart().setQuantity(item.id, 15);

    expect(quedo, 8);
    expect(cart().quantityOf(item.id), 8);
  });

  test('bajar a cero saca la línea del carrito', () {
    final item = product(max: 4);
    cart().add(item, quantity: 2);

    cart().setQuantity(item.id, 0);

    expect(container.read(cartProvider), isEmpty);
  });

  test('revalidar recorta las líneas largas y explica el ajuste', () {
    final item = product(max: 10, id: 1);
    cart().add(item, quantity: 6);

    // El servidor ahora informa que solo quedan 3.
    final report = cart().applyAvailability({1: 3});

    expect(cart().quantityOf(1), 3);
    expect(report.hasChanges, isTrue);
    expect(report.adjusted.single.from, 6);
    expect(report.adjusted.single.to, 3);
    expect(report.message, contains('Notebook'));
    expect(report.message, contains('Podés continuar'));
  });

  test('revalidar saca del carrito lo que se quedó sin stock', () {
    cart().add(product(max: 5, id: 1), quantity: 2);

    final report = cart().applyAvailability({1: 0});

    expect(container.read(cartProvider), isEmpty);
    expect(report.removed, ['Notebook']);
  });

  test('revalidar sin cambios no inventa un aviso', () {
    cart().add(product(max: 5, id: 1), quantity: 2);

    final report = cart().applyAvailability({1: 5});

    expect(report.hasChanges, isFalse);
    expect(cart().quantityOf(1), 2);
  });

  test('el tope alto no se anuncia como escasez', () {
    // Con 5000 en depósito el servidor publica el tope fijo: decir "quedan 99"
    // sería informar una escasez que no existe.
    final abundante = Product.fromJson({
      'id': 2,
      'nombre': 'Cable',
      'precio': 1000,
      'moneda': 'PYG',
      'disponible': true,
      'maxPorPedido': Product.maximoPorPedido,
    });
    final escaso = Product.fromJson({
      'id': 3,
      'nombre': 'Monitor',
      'precio': 1000,
      'moneda': 'PYG',
      'disponible': true,
      'maxPorPedido': 4,
    });

    expect(abundante.showsRemainingUnits, isFalse);
    expect(escaso.showsRemainingUnits, isTrue);
  });

  test('un carrito guardado sin el dato de tope sigue siendo usable', () {
    // Compatibilidad con lo persistido antes de que el servidor publicara el
    // campo: no se bloquea la compra, decide el checkout.
    final viejo = Product.fromJson({
      'id': 9,
      'nombre': 'Teclado',
      'precio': 1000,
      'moneda': 'PYG',
      'disponible': true,
    });

    expect(viejo.maxPerOrder, Product.maximoPorPedido);
    expect(cart().add(viejo).outcome, CartOutcome.added);
  });

  test('sin disponibilidad y sin tope el producto queda bloqueado', () {
    final agotado = Product.fromJson({
      'id': 10,
      'nombre': 'Placa',
      'precio': 1000,
      'moneda': 'PYG',
      'disponible': false,
    });

    expect(agotado.maxPerOrder, 0);
    expect(cart().add(agotado).outcome, CartOutcome.unavailable);
  });
}

/// Guarda en memoria: el carrito solo necesita que el almacen exista para
/// resolver a que usuario pertenece.
final class _MemorySecureStore implements SecureStore {
  final Map<String, String> _values = {};

  @override
  Future<void> clear() async => _values.clear();

  @override
  Future<void> delete(String key) async => _values.remove(key);

  @override
  Future<String?> read(String key) async => _values[key];

  @override
  Future<void> write(String key, String value) async => _values[key] = value;
}
