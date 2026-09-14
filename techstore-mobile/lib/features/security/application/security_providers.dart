import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../auth/application/session_controller.dart';
import '../data/security_repository.dart';
import '../domain/app_permissions.dart';

final securityRepositoryProvider = Provider<SecurityRepository>(
  (ref) => SecurityRepository(ref.watch(apiClientProvider)),
);

/// Los permisos de la sesión, pedidos una sola vez al servidor.
///
/// Se recalcula cuando cambia quién está adentro, no cada vez que cambia algo
/// de la sesión: renovar el token no cambia lo que una persona puede hacer, y
/// volver a pedirlos en cada renovación sería una consulta por hora sin motivo.
///
/// Cuando no hay nadie autenticado devuelve vacío en vez de fallar: así la
/// pantalla de login y el catálogo público no tienen que preguntar si hay
/// sesión antes de consultar un permiso.
final permisosProvider = FutureProvider<AppPermissions>((ref) async {
  final usuarioId = ref.watch(
    authSessionProvider.select((estado) => estado.user?.id),
  );
  if (usuarioId == null) return AppPermissions.vacio;
  return ref.watch(securityRepositoryProvider).mios();
});

/// Lo mismo, para quien necesita responder ya y no puede esperar.
///
/// Mientras la respuesta no llegó devuelve [AppPermissions.vacio], que no
/// habilita nada. Es a propósito: antes que mostrar una pantalla que el
/// servidor va a rechazar, se muestra de menos y aparece cuando se sabe.
AppPermissions permisosActuales(Ref ref) =>
    ref.read(permisosProvider).value ?? AppPermissions.vacio;
