import '../database/app_database.dart';

final class CacheStore {
  const CacheStore(this._database);

  final AppDatabase _database;

  Future<String?> read({required String scope, required String key}) async {
    final entry = await _database.readCache(scope, key);
    if (entry == null) return null;
    if (!entry.expiresAt.isAfter(DateTime.now())) return null;
    return entry.jsonValue;
  }

  /// Returns the last known value even after its TTL, exclusively as an
  /// explicit offline fallback. Callers must never present it as current data.
  Future<String?> readStale({
    required String scope,
    required String key,
  }) async {
    return (await _database.readCache(scope, key))?.jsonValue;
  }

  Future<void> write({
    required String scope,
    required String key,
    required String json,
    required Duration ttl,
  }) => _database.putCache(
    scope: scope,
    key: key,
    json: json,
    expiresAt: DateTime.now().add(ttl),
  );

  Future<int> clearScope(String scope) => _database.deleteScope(scope);

  Future<int> purgeExpired() => _database.purgeExpired();
}
