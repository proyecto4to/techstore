final class Product {
  const Product({
    required this.id,
    required this.name,
    required this.price,
    required this.currency,
    required this.available,
    this.description,
    this.imageUrl,
    this.brand,
  });

  factory Product.fromJson(Map<String, Object?> json) => Product(
    id: (json['id'] as num).toInt(),
    name: json['nombre'] as String? ?? '',
    description: json['descripcion'] as String?,
    price: (json['precio'] as num?)?.toDouble() ?? 0,
    currency: json['moneda'] as String? ?? 'PYG',
    available: json['disponible'] as bool? ?? false,
    imageUrl: json['imagenUrl'] as String?,
    brand: (json['marca'] as Map?)?['nombre'] as String?,
  );

  final int id;
  final String name;
  final String? description;
  final double price;
  final String currency;
  final bool available;
  final String? imageUrl;
  final String? brand;

  Map<String, Object?> toJson() => {
    'id': id,
    'nombre': name,
    'descripcion': description,
    'precio': price,
    'moneda': currency,
    'disponible': available,
    'imagenUrl': imageUrl,
    'marca': brand == null ? null : {'nombre': brand},
  };
}
