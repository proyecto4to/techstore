/// A dónde se transfiere, tal como lo cargó la tienda.
final class TransferDetails {
  const TransferDetails({
    required this.available,
    this.bank,
    this.holder,
    this.document,
    this.accountNumber,
    this.accountType,
    this.alias,
    this.instructions,
    this.hasQr = false,
  });

  factory TransferDetails.fromJson(Map<String, Object?> json) =>
      TransferDetails(
        available: json['disponible'] == true,
        bank: _texto(json['banco']),
        holder: _texto(json['titular']),
        document: _texto(json['documento']),
        accountNumber: _texto(json['numeroCuenta']),
        accountType: _texto(json['tipoCuenta']),
        alias: _texto(json['alias']),
        instructions: _texto(json['instrucciones']),
        hasQr: json['tieneQr'] == true,
      );

  /// Si alcanza para transferir. Sin esto no se ofrece la transferencia, y sin
  /// transferencia no hay envío a domicilio.
  final bool available;
  final String? bank;
  final String? holder;
  final String? document;
  final String? accountNumber;
  final String? accountType;

  /// El alias del SIPAP: cédula, celular o correo.
  final String? alias;
  final String? instructions;
  final bool hasQr;

  /// Los datos en el orden en que los pide una app de banco, sin los vacíos.
  List<(String, String)> get rows => [
    if (bank != null) ('Banco', bank!),
    if (holder != null) ('Titular', holder!),
    if (document != null) ('RUC o CI', document!),
    if (accountType != null) ('Tipo de cuenta', accountType!),
    if (accountNumber != null) ('N.º de cuenta', accountNumber!),
    if (alias != null) ('Alias', alias!),
  ];

  /// Todo junto, para pegarlo de una vez en la app del banco o en un chat.
  String asText({String? amount}) => [
    for (final (etiqueta, valor) in rows) '$etiqueta: $valor',
    if (amount != null) 'Monto: $amount',
  ].join('\n');

  Map<String, Object?> toJson() => {
    'banco': bank,
    'titular': holder,
    'documento': document,
    'numeroCuenta': accountNumber,
    'tipoCuenta': accountType,
    'alias': alias,
    'instrucciones': instructions,
  };
}

/// La revisión de un comprobante puntual.
enum ReceiptReview {
  inReview,
  approved,
  rejected;

  static ReceiptReview parse(String? raw) => switch (raw) {
    'APROBADO' => approved,
    'RECHAZADO' => rejected,
    _ => inReview,
  };
}

/// Un comprobante del pago, ya subido.
final class PaymentReceipt {
  const PaymentReceipt({
    required this.id,
    required this.mimeType,
    required this.sizeBytes,
    required this.review,
    this.uploadedAt,
    this.rejectionReason,
  });

  factory PaymentReceipt.fromJson(Map<String, Object?> json) => PaymentReceipt(
    id: (json['id'] as num).toInt(),
    mimeType: json['tipo']?.toString() ?? 'image/jpeg',
    sizeBytes: (json['tamanoBytes'] as num?)?.toInt() ?? 0,
    review: ReceiptReview.parse(json['estado']?.toString()),
    uploadedAt: DateTime.tryParse(json['subidoEn']?.toString() ?? ''),
    rejectionReason: _texto(json['motivoRechazo']),
  );

  final int id;
  final String mimeType;
  final int sizeBytes;
  final ReceiptReview review;
  final DateTime? uploadedAt;
  final String? rejectionReason;

  bool get isPdf => mimeType == 'application/pdf';
}

/// Dónde está la verificación del pago (`EstadoPago` en el backend).
enum PaymentState {
  /// Se cobra en persona.
  notRequired,
  waitingReceipt,
  inVerification,
  approved,
  rejected;

  static PaymentState parse(String? raw) => switch (raw?.trim().toUpperCase()) {
    'PENDIENTE_COMPROBANTE' => waitingReceipt,
    'EN_VERIFICACION' => inVerification,
    'APROBADO' => approved,
    'RECHAZADO' => rejected,
    _ => notRequired,
  };

  /// Si el cliente puede (y debe) mandar un comprobante ahora.
  bool get acceptsReceipt => this == waitingReceipt || this == rejected;
}

/// Un pedido con el pago por revisar, como lo ve quien cobra.
final class PendingPayment {
  const PendingPayment({
    required this.orderId,
    required this.number,
    required this.date,
    required this.total,
    required this.currency,
    required this.state,
    required this.customerName,
    required this.customerEmail,
    required this.receiptsInReview,
    this.delivery,
    this.rejectionReason,
  });

  factory PendingPayment.fromJson(Map<String, Object?> json) => PendingPayment(
    orderId: (json['pedidoId'] as num).toInt(),
    number: (json['numero'] as num?)?.toInt() ?? 0,
    date: DateTime.tryParse(json['fecha']?.toString() ?? '') ?? DateTime(1970),
    total: (json['total'] as num?)?.toDouble() ?? 0,
    currency: json['moneda']?.toString() ?? 'PYG',
    state: PaymentState.parse(json['estadoPago']?.toString()),
    customerName: json['clienteNombre']?.toString() ?? '',
    customerEmail: json['clienteEmail']?.toString() ?? '',
    receiptsInReview: (json['comprobantesEnRevision'] as num?)?.toInt() ?? 0,
    delivery: json['metodoEntrega']?.toString(),
    rejectionReason: _texto(json['pagoRechazoMotivo']),
  );

  final int orderId;
  final int number;
  final DateTime date;
  final double total;
  final String currency;
  final PaymentState state;
  final String customerName;
  final String customerEmail;
  final int receiptsInReview;
  final String? delivery;
  final String? rejectionReason;
}

/// Un paso del historial del pedido.
final class OrderEvent {
  const OrderEvent({required this.type, required this.date, this.detail});

  factory OrderEvent.fromJson(Map<String, Object?> json) => OrderEvent(
    type: json['tipo']?.toString() ?? '',
    detail: _texto(json['detalle']),
    date: DateTime.tryParse(json['fecha']?.toString() ?? '') ?? DateTime(1970),
  );

  final String type;
  final String? detail;
  final DateTime date;
}

String? _texto(Object? valor) {
  final texto = valor?.toString().trim();
  return texto == null || texto.isEmpty ? null : texto;
}
