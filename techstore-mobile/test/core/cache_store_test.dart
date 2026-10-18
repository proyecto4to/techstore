import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/cache/cache_store.dart';
import 'package:techstore_mobile/core/database/app_database.dart';

void main() {
  late AppDatabase database;
  late CacheStore cache;

  setUp(() {
    database = AppDatabase(NativeDatabase.memory());
    cache = CacheStore(database);
  });

  tearDown(() => database.close());

  test('aísla la caché por tenant y usuario', () async {
    await cache.write(
      scope: 'tenant:1:user:10',
      key: 'products',
      json: '[1]',
      ttl: const Duration(minutes: 5),
    );
    await cache.write(
      scope: 'tenant:2:user:10',
      key: 'products',
      json: '[2]',
      ttl: const Duration(minutes: 5),
    );

    expect(await cache.read(scope: 'tenant:1:user:10', key: 'products'), '[1]');
    expect(await cache.read(scope: 'tenant:2:user:10', key: 'products'), '[2]');
  });

  test('una instalación nueva no tiene datos guardados que mostrar', () async {
    // El cartel de «sin conexión» prometía datos guardados incluso con la app
    // recién instalada, cuando no hay ninguno.
    expect(await cache.hasAnyData(), isFalse);

    await cache.write(
      scope: 'public',
      key: 'catalog',
      json: '[1]',
      ttl: const Duration(minutes: 5),
    );

    expect(await cache.hasAnyData(), isTrue);
  });

  test('una entrada vencida sigue contando como dato guardado', () async {
    // Vencida es justamente la que se ofrece de respaldo cuando no hay red.
    await database.putCache(
      scope: 'public',
      key: 'catalog',
      json: '[1]',
      expiresAt: DateTime.now().subtract(const Duration(minutes: 1)),
    );

    expect(await cache.hasAnyData(), isTrue);
  });

  test('no devuelve entradas expiradas', () async {
    await database.putCache(
      scope: 'public',
      key: 'catalog',
      json: '[]',
      expiresAt: DateTime.now().subtract(const Duration(seconds: 1)),
    );

    expect(await cache.read(scope: 'public', key: 'catalog'), isNull);
    expect(
      await cache.readStale(scope: 'public', key: 'catalog'),
      '[]',
      reason: 'el fallback offline conserva el último dato conocido',
    );
  });
}
