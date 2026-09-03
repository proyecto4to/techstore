/// La regla de contraseña, en un solo lugar.
///
/// Estaba escrita como una cadena de condiciones dentro de la pantalla de
/// reseteo y otra vez como texto fijo debajo del formulario. Cuando la regla
/// vive en dos lados, tarde o temprano el texto dice una cosa y la validación
/// exige otra.
library;

/// Longitud mínima que exige el backend.
const int minimoCaracteres = 8;

/// Los requisitos dichos en palabras, para mostrarlos *antes* de escribir.
const String requisitosPassword =
    'Al menos $minimoCaracteres caracteres, con una mayúscula, '
    'una minúscula y un número.';

/// Si la contraseña cumple lo que el servidor va a aceptar.
bool passwordValida(String password) =>
    password.length >= minimoCaracteres &&
    password.contains(RegExp('[a-z]')) &&
    password.contains(RegExp('[A-Z]')) &&
    password.contains(RegExp('[0-9]'));

/// Qué le falta a la contraseña, o `null` si está bien.
///
/// Devuelve el motivo concreto en vez de un «no cumple los requisitos»
/// genérico: si falta una mayúscula, lo que hay que decir es eso.
String? errorDePassword(String password) {
  if (password.isEmpty) return null;
  if (password.length < minimoCaracteres) {
    return 'Le faltan caracteres: tiene que tener al menos $minimoCaracteres.';
  }
  if (!password.contains(RegExp('[A-Z]'))) {
    return 'Agregale una letra mayúscula.';
  }
  if (!password.contains(RegExp('[a-z]'))) {
    return 'Agregale una letra minúscula.';
  }
  if (!password.contains(RegExp('[0-9]'))) {
    return 'Agregale un número.';
  }
  return null;
}
