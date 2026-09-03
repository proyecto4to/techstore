/** Longitud mínima que exige el backend (`RegistroRequest`). */
export const MINIMO_CARACTERES = 8

/** Los requisitos en palabras, para decirlos *antes* de que la persona escriba. */
export const REQUISITOS_PASSWORD =
  `Al menos ${MINIMO_CARACTERES} caracteres, con una mayúscula, una minúscula y un número.`

export const passwordValida = (value) =>
  value.length >= MINIMO_CARACTERES &&
  /[a-z]/.test(value) &&
  /[A-Z]/.test(value) &&
  /\d/.test(value)

export const passwordsCoinciden = (password, confirmation) => password === confirmation

/**
 * Qué le falta a la contraseña, o `null` si está bien.
 *
 * Devuelve el motivo concreto en vez de un «no cumple los requisitos»: si lo
 * único que falta es una mayúscula, eso es lo que hay que decir. Con el campo
 * vacío devuelve `null`, porque marcarlo en rojo antes de que escriba una letra
 * es ruido.
 */
export function errorDePassword(value) {
  if (!value) return null
  if (value.length < MINIMO_CARACTERES) {
    return `Le faltan caracteres: tiene que tener al menos ${MINIMO_CARACTERES}.`
  }
  if (!/[A-Z]/.test(value)) return 'Agregale una letra mayúscula.'
  if (!/[a-z]/.test(value)) return 'Agregale una letra minúscula.'
  if (!/\d/.test(value)) return 'Agregale un número.'
  return null
}

/** Si la confirmación no coincide, con el campo ya empezado. */
export function errorDeConfirmacion(password, confirmacion) {
  if (!confirmacion) return null
  return passwordsCoinciden(password, confirmacion)
    ? null
    : 'Las dos contraseñas tienen que ser iguales.'
}
