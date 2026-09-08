final class DeliveryAddress {
  const DeliveryAddress({
    required this.id,
    required this.recipient,
    required this.phone,
    this.description,
    this.department,
    this.city,
    this.line1,
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
        description: json['descripcion'] as String?,
        department: json['departamento'] as String?,
        city: json['ciudad'] as String?,
        line1: json['direccionLinea1'] as String?,
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

  /// Cómo le dice la persona a este lugar: «Casa», «Trabajo».
  ///
  /// Es lo único que el mapa no puede deducir, y es lo que encabeza la ficha:
  /// entre dos calles parecidas cuesta elegir; entre «Casa» y «Trabajo», no.
  final String? description;

  // Lo que sigue se deduce del punto marcado y puede faltar: si el servicio de
  // direcciones no contesta, la ubicación igual queda guardada.
  final String? department;
  final String? city;
  final String? line1;
  final String? neighborhood;
  final String? line2;
  final String? houseNumber;
  final String? reference;
  final String? postalCode;
  final double? latitude;
  final double? longitude;
  final bool primary;

  /// El título de la ficha.
  String get label => description?.trim().isNotEmpty ?? false
      ? description!.trim()
      : (line1?.trim().isNotEmpty ?? false ? line1!.trim() : 'Dirección');

  /// La calle y la ciudad en una línea, con lo que haya.
  String get calleYCiudad {
    final partes = [
      if (line1 != null && line1!.trim().isNotEmpty) line1!.trim(),
      if (houseNumber != null && houseNumber!.trim().isNotEmpty)
        houseNumber!.trim(),
      if (neighborhood != null && neighborhood!.trim().isNotEmpty)
        neighborhood!.trim(),
      if (city != null && city!.trim().isNotEmpty) city!.trim(),
      if (department != null && department!.trim().isNotEmpty)
        department!.trim(),
    ];
    return partes.isEmpty ? 'Ubicación marcada en el mapa' : partes.join(', ');
  }
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
