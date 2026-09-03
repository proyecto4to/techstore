export const passwordValida = (value) =>
  value.length >= 8 && /[a-z]/.test(value) && /[A-Z]/.test(value) && /\d/.test(value)

export const passwordsCoinciden = (password, confirmation) =>
  password === confirmation
