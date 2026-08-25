import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../data/invoices_repository.dart';
import '../domain/invoice.dart';

final invoicesRepositoryProvider = Provider<InvoicesRepository>(
  (ref) => InvoicesRepository(ref.watch(apiClientProvider)),
);

final invoicesProvider = FutureProvider.autoDispose
    .family<InvoicePageResult, int>(
      (ref, page) => ref.watch(invoicesRepositoryProvider).list(page: page),
    );

final invoiceProvider = FutureProvider.autoDispose.family<Invoice, int>(
  (ref, id) => ref.watch(invoicesRepositoryProvider).get(id),
);
