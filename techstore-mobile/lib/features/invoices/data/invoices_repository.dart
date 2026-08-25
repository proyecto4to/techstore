import '../../../core/network/api_client.dart';
import '../domain/invoice.dart';

final class InvoicesRepository {
  const InvoicesRepository(this._api);

  final ApiClient _api;

  Future<InvoicePageResult> list({int page = 0, int size = 20}) async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/facturas',
      queryParameters: {'page': page, 'size': size, 'sort': 'fecha,DESC'},
    );
    return InvoicePageResult.fromJson(response.data!);
  }

  Future<Invoice> get(int id) async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/facturas/$id',
    );
    return Invoice.fromJson(response.data!);
  }
}
