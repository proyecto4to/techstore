/// Traducción de los enlaces `techstore://` a rutas internas.
///
/// Vive aparte del router porque tiene dos consumidores: el `redirect` de
/// GoRouter, para los enlaces que llegan desde afuera (push, correo), y el
/// centro de notificaciones, que recibe el mismo URI en cada aviso y necesita
/// abrir la pantalla correspondiente sin repetir el mapeo.
///
/// Devuelve `null` si el enlace no es nuestro o si el host no está previsto:
/// quien llama decide qué hacer con eso.
String? rutaDeEnlaceTechstore(Uri uri) {
  if (uri.scheme != 'techstore') return null;
  final id = uri.pathSegments.firstOrNull;
  switch (uri.host) {
    case 'app'
        when uri.path == '/password/reset' || uri.path == 'password/reset':
      final token = uri.queryParameters['token'] ?? '';
      return '/password/reset?token=${Uri.encodeQueryComponent(token)}';
    case 'pedido' when id != null:
      return '/orders/$id';
    case 'factura':
      return id == null ? '/invoices' : '/invoices/$id';
    case 'notificaciones':
      return '/notifications';
    case 'reparto' when id != null:
      return '/courier/deliveries/$id';
    case 'transferencia':
      return '/courier/cash';
    case 'vehiculo':
      return '/courier/vehicles';
    case 'chat':
      return id == null ? '/chat' : '/chat/$id';
  }
  return null;
}
