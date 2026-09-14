import '../../../core/network/api_client.dart';
import '../domain/app_permissions.dart';

/// Lo que la app le pregunta al backend sobre permisos.
///
/// Dos clases de consulta, con dos alcances distintos:
///
/// - `mios()`, que cualquiera puede pedir para sí y vive bajo `/auth` porque
///   preguntar qué puedo hacer yo no es administrar seguridad;
/// - el resto, que solo responde a un administrador y está bajo `/seguridad`.
final class SecurityRepository {
  const SecurityRepository(this._api);

  final ApiClient _api;

  /// Los permisos de la sesión actual.
  Future<AppPermissions> mios() async {
    final respuesta = await _api.get<Map<String, Object?>>(
      '/api/v1/auth/mis-permisos',
    );
    return AppPermissions.fromJson(respuesta.data ?? const {});
  }

  /// Módulos, acciones y pantallas que existen para configurar.
  Future<Map<String, Object?>> catalogo() async {
    final respuesta = await _api.get<Map<String, Object?>>(
      '/api/v1/seguridad/catalogo',
    );
    return respuesta.data ?? const {};
  }

  Future<List<String>> roles() async {
    final respuesta = await _api.get<List<dynamic>>('/api/v1/seguridad/roles');
    return (respuesta.data ?? const []).map((e) => e.toString()).toList();
  }

  Future<List<Map<String, Object?>>> rolesDetallados() async {
    final respuesta = await _api.get<List<dynamic>>(
      '/api/v1/seguridad/roles/detalle',
    );
    return (respuesta.data ?? const [])
        .cast<Map<String, Object?>>()
        .toList(growable: false);
  }

  Future<Map<String, Object?>> matrizDelRol(String rol) async {
    final respuesta = await _api.get<Map<String, Object?>>(
      '/api/v1/seguridad/roles/$rol/permisos',
    );
    return respuesta.data ?? const {};
  }

  Future<Map<String, Object?>> guardarPermisoDelRol(
    String rol,
    Map<String, Object?> cuerpo,
  ) async {
    final respuesta = await _api.put<Map<String, Object?>>(
      '/api/v1/seguridad/roles/$rol/permisos',
      data: cuerpo,
    );
    return respuesta.data ?? const {};
  }

  Future<Map<String, Object?>> guardarPantallaDelRol(
    String rol,
    Map<String, Object?> cuerpo,
  ) async {
    final respuesta = await _api.put<Map<String, Object?>>(
      '/api/v1/seguridad/roles/$rol/pantallas',
      data: cuerpo,
    );
    return respuesta.data ?? const {};
  }

  /// Usuarios para la pantalla de seguridad, con su filtro de texto.
  Future<List<Map<String, Object?>>> usuarios({String busqueda = ''}) async {
    final respuesta = await _api.get<Map<String, Object?>>(
      '/api/v1/usuarios',
      queryParameters: {
        'size': 50,
        if (busqueda.trim().isNotEmpty) 'q': busqueda.trim(),
      },
    );
    final contenido = (respuesta.data?['content'] as List?) ?? const [];
    return contenido.cast<Map<String, Object?>>().toList(growable: false);
  }

  /// Los permisos efectivos de una persona, con el origen de cada uno.
  Future<Map<String, Object?>> permisosDelUsuario(int usuarioId) async {
    final respuesta = await _api.get<Map<String, Object?>>(
      '/api/v1/seguridad/usuarios/$usuarioId/permisos',
    );
    return respuesta.data ?? const {};
  }

  /// Concede (`permitido` en true) o restringe una acción a esa persona.
  Future<Map<String, Object?>> guardarPermisoDelUsuario(
    int usuarioId, {
    required String modulo,
    required String accion,
    required bool permitido,
    String? motivo,
  }) async {
    final respuesta = await _api.put<Map<String, Object?>>(
      '/api/v1/seguridad/usuarios/$usuarioId/permisos',
      data: {
        'modulo': modulo,
        'accion': accion,
        'permitido': permitido,
        'motivo': motivo,
      },
    );
    return respuesta.data ?? const {};
  }

  /// Le saca la excepción: vuelve a valer lo que diga su rol.
  Future<Map<String, Object?>> quitarPermisoDelUsuario(
    int usuarioId, {
    required String modulo,
    required String accion,
  }) async {
    final respuesta = await _api.delete<Map<String, Object?>>(
      '/api/v1/seguridad/usuarios/$usuarioId/permisos/$modulo/$accion',
    );
    return respuesta.data ?? const {};
  }
}
