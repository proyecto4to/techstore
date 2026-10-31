final class OrderSummary {
  const OrderSummary({
    required this.id,
    required this.number,
    required this.status,
    required this.date,
    required this.total,
    required this.currency,
    this.paymentStatus,
    this.deliveryMethod,
    this.paymentMethod,
  });

  factory OrderSummary.fromJson(Map<String, Object?> json) => OrderSummary(
    id: (json['id'] as num).toInt(),
    number: (json['numero'] as num?)?.toInt() ?? 0,
    status: json['estado'] as String? ?? '',
    date: DateTime.tryParse(json['fecha'] as String? ?? '') ?? DateTime(1970),
    total: (json['total'] as num?)?.toDouble() ?? 0,
    currency: json['moneda'] as String? ?? 'PYG',
    paymentStatus: json['estadoPago'] as String?,
    deliveryMethod: json['metodoEntrega'] as String?,
    paymentMethod: json['metodoPago'] as String?,
  );

  final int id;
  final int number;
  final String status;
  final DateTime date;
  final double total;
  final String currency;

  /// La verificación del pago por transferencia (`EstadoPago` en el backend).
  final String? paymentStatus;
  final String? deliveryMethod;
  final String? paymentMethod;
}
