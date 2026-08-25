enum AppRole {
  admin,
  cashier,
  customer;

  factory AppRole.fromBackend(String role) => switch (role.toLowerCase()) {
    'admin' => AppRole.admin,
    'cajero' => AppRole.cashier,
    _ => AppRole.customer,
  };

  bool get canOperateStore => this == admin || this == cashier;
  bool get canAdminister => this == admin;
}

final class AuthUser {
  const AuthUser({
    required this.id,
    required this.email,
    required this.firstName,
    required this.lastName,
    required this.role,
    this.ruc,
    this.businessName,
  });

  factory AuthUser.fromJson(Map<String, Object?> json) => AuthUser(
    id: (json['id'] as num).toInt(),
    email: json['email']! as String,
    firstName: json['nombre']! as String,
    lastName: json['apellido']! as String,
    role: AppRole.fromBackend(json['rol']! as String),
    ruc: json['ruc'] as String?,
    businessName: json['razonSocial'] as String?,
  );

  final int id;
  final String email;
  final String firstName;
  final String lastName;
  final AppRole role;
  final String? ruc;
  final String? businessName;

  String get fullName => '$firstName $lastName'.trim();
}
