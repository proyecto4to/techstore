/// Los módulos sobre los que el servidor da permisos.
///
/// Son constantes y no un enum porque la lista la define el backend: si mañana
/// aparece uno nuevo, la app lo recibe igual dentro de `permitidos` y no se
/// rompe nada. Acá están solo los que la app necesita nombrar para decidir qué
/// mostrar.
abstract final class Modulos {
  static const productos = 'PRODUCTOS';
  static const pedidos = 'PEDIDOS';
  static const compras = 'COMPRAS';
  static const proveedores = 'PROVEEDORES';
  static const caja = 'CAJA';
  static const facturas = 'FACTURAS';
  static const usuarios = 'USUARIOS';
  static const empresa = 'EMPRESA';
  static const envios = 'ENVIOS';
  static const repartos = 'REPARTOS';
  static const vehiculos = 'VEHICULOS';
  static const auditoria = 'AUDITORIA';
  static const contabilidad = 'CONTABILIDAD';
  static const configuracion = 'CONFIGURACION';
  static const conversaciones = 'CONVERSACIONES';
  static const reportes = 'REPORTES';
  static const seguridad = 'SEGURIDAD';
}

abstract final class Acciones {
  static const ver = 'VER';
  static const crear = 'CREAR';
  static const editar = 'EDITAR';
  static const eliminar = 'ELIMINAR';
}

/// Lo que esta persona puede hacer, según lo respondió el servidor.
///
/// La app no calcula permisos: los pide. El rol, las excepciones individuales y
/// la herencia se resuelven en el backend, que es el mismo que después deja
/// pasar o rechaza cada operación. Acá solo se guarda la respuesta para decidir
/// qué dibujar.
///
/// Esconder una pantalla no es seguridad. Si alguien llega igual —un enlace
/// profundo, una versión vieja de la app, una petición hecha a mano— la
/// operación falla en el servidor, que es donde tiene que fallar.
final class AppPermissions {
  const AppPermissions({
    required this.rol,
    required this.permitidos,
    required this.pantallas,
  });

  factory AppPermissions.fromJson(Map<String, Object?> json) {
    return AppPermissions(
      rol: (json['rol'] as String?) ?? '',
      permitidos: ((json['permitidos'] as List?) ?? const [])
          .map((e) => e.toString().toLowerCase())
          .toSet(),
      pantallas: ((json['pantallas'] as List?) ?? const [])
          .map((e) => e.toString())
          .toSet(),
    );
  }

  /// Lo que se sabe antes de preguntar: nada.
  static const vacio = AppPermissions(
    rol: '',
    permitidos: <String>{},
    pantallas: <String>{},
  );

  final String rol;

  /// Claves `modulo.accion` en minúsculas, tal como las manda el servidor.
  final Set<String> permitidos;

  /// Las pantallas habilitadas para su rol.
  final Set<String> pantallas;

  bool puede(String modulo, [String accion = Acciones.ver]) =>
      permitidos.contains('$modulo.$accion'.toLowerCase());

  bool vePantalla(String pantalla) => pantallas.contains(pantalla);

  bool get administraSeguridad => puede(Modulos.seguridad, Acciones.editar);
}
