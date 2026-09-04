/// Lleva la cuenta de las salidas que la propia app provocó.
///
/// La sesión se cierra cuando la app pasa a segundo plano, pero abrir la
/// cámara, la galería, un diálogo de permisos o el visor de un PDF también
/// manda la app a segundo plano. Sin esta distinción, adjuntar el comprobante
/// de un pago cerraría la sesión en mitad de la operación y se perdería todo
/// lo cargado.
///
/// Es un contador y no un booleano porque una pantalla encadena salidas —elegir
/// la foto y después recortarla— y la primera en terminar no debe habilitar el
/// cierre mientras la segunda sigue abierta.
///
/// No hay margen por tiempo a propósito: solo se exime lo que la app pidió, no
/// «los primeros N segundos», que sería una puerta abierta a que alguien tome
/// el teléfono apenas cambiada la aplicación.
final class AppExcursion {
  int _abiertas = 0;

  /// Si en este momento hay una salida iniciada por la app.
  bool get enCurso => _abiertas > 0;

  /// Corre [accion] marcando que la salida la pidió la app.
  ///
  /// El descuento va en `finally`: si la persona cancela o la operación falla,
  /// la marca igual se levanta y la app no queda sin cierre por sesión.
  Future<T> durante<T>(Future<T> Function() accion) async {
    _abiertas++;
    try {
      return await accion();
    } finally {
      if (_abiertas > 0) _abiertas--;
    }
  }
}
