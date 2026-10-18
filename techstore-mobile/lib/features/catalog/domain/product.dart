final class Product {
  const Product({
    required this.id,
    required this.name,
    required this.price,
    required this.currency,
    required this.available,
    this.maxPerOrder = 0,
    this.description,
    this.imageUrl,
    this.brand,
    this.listPrice,
    this.promotionName,
    this.promotionPercentage,
    this.promotionEndsAt,
  });

  factory Product.fromJson(Map<String, Object?> json) {
    final available = json['disponible'] as bool? ?? false;
    final rawMax = (json['maxPorPedido'] as num?)?.toInt();
    final promocion = json['promocion'] as Map?;
    return Product(
      id: (json['id'] as num).toInt(),
      name: json['nombre'] as String? ?? '',
      description: json['descripcion'] as String?,
      price: (json['precio'] as num?)?.toDouble() ?? 0,
      currency: json['moneda'] as String? ?? 'PYG',
      available: available,
      // Un carrito guardado antes de que el servidor publicara el tope, o una
      // respuesta cacheada vieja, no traen el dato. En ese caso no inventamos
      // un limite: dejamos pasar hasta el maximo y que el checkout decida, que
      // es exactamente como se comportaba la app antes de este campo.
      maxPerOrder: rawMax ?? (available ? maximoPorPedido : 0),
      imageUrl: json['imagenUrl'] as String?,
      brand: (json['marca'] as Map?)?['nombre'] as String?,
      listPrice: (json['precioLista'] as num?)?.toDouble(),
      promotionName: promocion?['nombre'] as String?,
      promotionPercentage: (promocion?['porcentaje'] as num?)?.toDouble(),
      promotionEndsAt: DateTime.tryParse(promocion?['hasta']?.toString() ?? ''),
    );
  }

  /// Espeja `Producto.MAXIMO_POR_PEDIDO` del backend: el checkout rechaza mas
  /// que esto, asi que la interfaz no debe ofrecerlo.
  static const int maximoPorPedido = 99;

  final int id;
  final String name;
  final String? description;

  /// Lo que se va a cobrar por una unidad: el servidor ya le aplico la
  /// promocion vigente, si habia alguna.
  final double price;
  final String currency;
  final bool available;

  /// Cuantas unidades acepta el servidor en una linea de pedido ahora mismo.
  /// No es el stock del deposito: viene techado en [maximoPorPedido].
  final int maxPerOrder;

  final String? imageUrl;
  final String? brand;

  /// El precio sin promocion, para mostrarlo tachado. Nulo si no hay ninguna.
  final double? listPrice;
  final String? promotionName;
  final double? promotionPercentage;
  final DateTime? promotionEndsAt;

  /// Si hay una rebaja que mostrar.
  ///
  /// Exige que el precio de lista sea mayor: una promocion que por redondeo no
  /// cambia el precio no tiene por que dibujar un «antes» identico al «ahora».
  bool get hasPromotion =>
      listPrice != null && promotionPercentage != null && listPrice! > price;

  /// Si el numero que publica el servidor es lo que realmente queda en gondola
  /// y no el tope fijo. Solo entonces tiene sentido decirle al cliente
  /// "quedan N": con el tope, N no significa nada sobre las existencias.
  bool get showsRemainingUnits =>
      maxPerOrder > 0 && maxPerOrder < maximoPorPedido;

  Product copyWith({int? maxPerOrder, bool? available}) => Product(
    id: id,
    name: name,
    description: description,
    price: price,
    currency: currency,
    available: available ?? this.available,
    maxPerOrder: maxPerOrder ?? this.maxPerOrder,
    imageUrl: imageUrl,
    brand: brand,
    listPrice: listPrice,
    promotionName: promotionName,
    promotionPercentage: promotionPercentage,
    promotionEndsAt: promotionEndsAt,
  );

  Map<String, Object?> toJson() => {
    'id': id,
    'nombre': name,
    'descripcion': description,
    'precio': price,
    'moneda': currency,
    'disponible': available,
    'maxPorPedido': maxPerOrder,
    'imagenUrl': imageUrl,
    'marca': brand == null ? null : {'nombre': brand},
    'precioLista': listPrice,
    if (promotionName != null)
      'promocion': {
        'nombre': promotionName,
        'porcentaje': promotionPercentage,
        'hasta': promotionEndsAt?.toIso8601String(),
      },
  };
}
