final class DeliveryAddress {
  const DeliveryAddress({
    required this.id,
    required this.recipient,
    required this.phone,
    required this.department,
    required this.city,
    required this.line1,
    this.neighborhood,
    this.line2,
    this.houseNumber,
    this.reference,
    this.postalCode,
    this.latitude,
    this.longitude,
    this.primary = false,
  });

  factory DeliveryAddress.fromJson(Map<String, Object?> json) =>
      DeliveryAddress(
        id: (json['id'] as num).toInt(),
        recipient: json['nombreDestinatario'] as String? ?? '',
        phone: json['telefono'] as String? ?? '',
        department: json['departamento'] as String? ?? '',
        city: json['ciudad'] as String? ?? '',
        line1: json['direccionLinea1'] as String? ?? '',
        neighborhood: json['barrio'] as String?,
        line2: json['direccionLinea2'] as String?,
        houseNumber: json['numeroCasa'] as String?,
        reference: json['referencia'] as String?,
        postalCode: json['codigoPostal'] as String?,
        latitude: (json['latitud'] as num?)?.toDouble(),
        longitude: (json['longitud'] as num?)?.toDouble(),
        primary: json['principal'] as bool? ?? false,
      );

  final int id;
  final String recipient;
  final String phone;
  final String department;
  final String city;
  final String line1;
  final String? neighborhood;
  final String? line2;
  final String? houseNumber;
  final String? reference;
  final String? postalCode;
  final double? latitude;
  final double? longitude;
  final bool primary;
}

final class ShippingQuote {
  const ShippingQuote({
    required this.tariffId,
    required this.name,
    required this.cost,
    required this.currency,
    this.estimatedFrom,
    this.estimatedTo,
  });

  factory ShippingQuote.fromJson(Map<String, Object?> json) => ShippingQuote(
    tariffId: (json['tarifaId'] as num).toInt(),
    name: json['nombre'] as String? ?? '',
    cost: (json['costo'] as num?)?.toDouble() ?? 0,
    currency: json['moneda'] as String? ?? 'PYG',
    estimatedFrom: json['fechaEstimadaDesde'] as String?,
    estimatedTo: json['fechaEstimadaHasta'] as String?,
  );

  final int tariffId;
  final String name;
  final double cost;
  final String currency;
  final String? estimatedFrom;
  final String? estimatedTo;
}
