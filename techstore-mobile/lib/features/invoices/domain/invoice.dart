final class Invoice {
  const Invoice({
    required this.id,
    required this.number,
    required this.saleCondition,
    required this.orderNumber,
    required this.date,
    required this.currency,
    required this.subtotal,
    required this.tax,
    required this.total,
    required this.status,
    this.stamping,
    this.receiverRuc,
    this.receiverName,
    this.receiverEmail,
  });

  factory Invoice.fromJson(Map<String, Object?> json) => Invoice(
    id: (json['id'] as num).toInt(),
    number: json['numero']?.toString() ?? '',
    saleCondition: json['condicionVenta']?.toString() ?? '',
    stamping: json['timbrado']?.toString(),
    orderNumber: (json['pedidoNumero'] as num?)?.toInt() ?? 0,
    date: DateTime.tryParse(json['fecha']?.toString() ?? '') ?? DateTime(1970),
    currency: json['moneda']?.toString() ?? 'PYG',
    subtotal: _amount(json['subtotal']),
    tax: _amount(json['iva']),
    total: _amount(json['total']),
    status: json['estado']?.toString() ?? '',
    receiverRuc: json['receptorRuc']?.toString(),
    receiverName: json['receptorNombre']?.toString(),
    receiverEmail: json['receptorEmail']?.toString(),
  );

  final int id;
  final String number;
  final String saleCondition;
  final String? stamping;
  final int orderNumber;
  final DateTime date;
  final String currency;
  final double subtotal;
  final double tax;
  final double total;
  final String status;
  final String? receiverRuc;
  final String? receiverName;
  final String? receiverEmail;

  String get receiverLabel {
    final name = receiverName?.trim();
    return name == null || name.isEmpty ? 'Consumidor final' : name;
  }

  static double _amount(Object? value) => switch (value) {
    final num number => number.toDouble(),
    final String text => double.tryParse(text) ?? 0,
    _ => 0,
  };
}

final class InvoicePageResult {
  const InvoicePageResult({
    required this.items,
    required this.page,
    required this.totalElements,
    required this.totalPages,
  });

  factory InvoicePageResult.fromJson(Map<String, Object?> json) =>
      InvoicePageResult(
        items: (json['content'] as List? ?? const [])
            .map(
              (item) =>
                  Invoice.fromJson(Map<String, Object?>.from(item as Map)),
            )
            .toList(growable: false),
        page: (json['page'] as num?)?.toInt() ?? 0,
        totalElements: (json['totalElements'] as num?)?.toInt() ?? 0,
        totalPages: (json['totalPages'] as num?)?.toInt() ?? 0,
      );

  final List<Invoice> items;
  final int page;
  final int totalElements;
  final int totalPages;
}
