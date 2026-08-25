import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

part 'app_database.g.dart';

class CacheEntries extends Table {
  TextColumn get scope => text()();
  TextColumn get cacheKey => text()();
  TextColumn get jsonValue => text()();
  DateTimeColumn get expiresAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();

  @override
  Set<Column<Object>> get primaryKey => {scope, cacheKey};
}

class PendingOperations extends Table {
  TextColumn get id => text()();
  TextColumn get scope => text()();
  TextColumn get kind => text()();
  TextColumn get jsonValue => text()();
  IntColumn get attempts => integer().withDefault(const Constant(0))();
  DateTimeColumn get nextAttemptAt => dateTime().nullable()();
  TextColumn get lastError => text().nullable()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();

  @override
  Set<Column<Object>> get primaryKey => {id};
}

@DriftDatabase(tables: [CacheEntries, PendingOperations])
class AppDatabase extends _$AppDatabase {
  AppDatabase([QueryExecutor? executor]) : super(executor ?? _openConnection());

  @override
  int get schemaVersion => 2;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (migrator) => migrator.createAll(),
    onUpgrade: (migrator, from, to) async {
      if (from < 2) await migrator.createTable(pendingOperations);
    },
  );

  Future<void> putCache({
    required String scope,
    required String key,
    required String json,
    required DateTime expiresAt,
  }) => into(cacheEntries).insertOnConflictUpdate(
    CacheEntriesCompanion.insert(
      scope: scope,
      cacheKey: key,
      jsonValue: json,
      expiresAt: expiresAt,
    ),
  );

  Future<CacheEntry?> readCache(String scope, String key) =>
      (select(cacheEntries)..where(
            (entry) => entry.scope.equals(scope) & entry.cacheKey.equals(key),
          ))
          .getSingleOrNull();

  Future<int> deleteScope(String scope) =>
      (delete(cacheEntries)..where((entry) => entry.scope.equals(scope))).go();

  Future<int> purgeExpired([DateTime? now]) =>
      (delete(cacheEntries)..where(
            (entry) =>
                entry.expiresAt.isSmallerThanValue(now ?? DateTime.now()),
          ))
          .go();

  Future<void> enqueueOperation(PendingOperationsCompanion operation) =>
      into(pendingOperations)
          .insert(operation, mode: InsertMode.insertOrIgnore);

  Future<List<PendingOperation>> dueOperations(String scope, {DateTime? now}) =>
      (select(pendingOperations)
            ..where(
              (operation) =>
                  operation.scope.equals(scope) &
                  (operation.nextAttemptAt.isNull() |
                      operation.nextAttemptAt.isSmallerOrEqualValue(
                        now ?? DateTime.now(),
                      )),
            )
            ..orderBy([(operation) => OrderingTerm.asc(operation.createdAt)]))
          .get();

  Stream<int> watchPendingCount(String scope) =>
      (selectOnly(pendingOperations)
            ..addColumns([pendingOperations.id.count()])
            ..where(pendingOperations.scope.equals(scope)))
          .watchSingle()
          .map((row) => row.read(pendingOperations.id.count()) ?? 0);

  Future<int> deleteOperation(String id) => (delete(
    pendingOperations,
  )..where((operation) => operation.id.equals(id))).go();

  Future<int> retryOperation({
    required String id,
    required int attempts,
    required DateTime nextAttemptAt,
    required String error,
  }) =>
      (update(
        pendingOperations,
      )..where((operation) => operation.id.equals(id))).write(
        PendingOperationsCompanion(
          attempts: Value(attempts),
          nextAttemptAt: Value(nextAttemptAt),
          lastError: Value(error),
        ),
      );

  Future<int> deleteOperationsForScope(String scope) => (delete(
    pendingOperations,
  )..where((operation) => operation.scope.equals(scope))).go();
}

LazyDatabase _openConnection() => LazyDatabase(() async {
  final directory = await getApplicationSupportDirectory();
  return NativeDatabase.createInBackground(
    File(path.join(directory.path, 'techstore.sqlite')),
  );
});
