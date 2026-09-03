import { describe, expect, it } from 'vitest'
import {
  MINIMO_CARACTERES,
  REQUISITOS_PASSWORD,
  errorDeConfirmacion,
  errorDePassword,
  passwordValida,
  passwordsCoinciden,
} from './password'

describe('reglas de contraseña', () => {
  it('exige longitud, mayúscula, minúscula y número', () => {
    expect(passwordValida('NuevaClave1')).toBe(true)
    expect(passwordValida('nuevaclave1')).toBe(false)
    expect(passwordValida('NUEVACLAVE1')).toBe(false)
    expect(passwordValida('NuevaClave')).toBe(false)
    expect(passwordValida('Corta1')).toBe(false)
  })

  it('rechaza una confirmación distinta', () => {
    expect(passwordsCoinciden('NuevaClave1', 'NuevaClave1')).toBe(true)
    expect(passwordsCoinciden('NuevaClave1', 'OtraClave1')).toBe(false)
  })

  it('rechaza lo que el backend también rechaza', () => {
    // El registro decía «mínimo 8 caracteres» y confiaba en `minLength`, así
    // que «12345678» pasaba en el navegador y el servidor lo rechazaba después.
    expect(passwordValida('12345678')).toBe(false)
  })
})

describe('errorDePassword', () => {
  it('no dice nada con el campo vacío', () => {
    expect(errorDePassword('')).toBeNull()
  })

  it('nombra lo que falta en vez de rechazar en general', () => {
    expect(errorDePassword('Ab1')).toContain('caracteres')
    expect(errorDePassword('segura123')).toContain('mayúscula')
    expect(errorDePassword('SEGURA123')).toContain('minúscula')
    expect(errorDePassword('SeguraClave')).toContain('número')
  })

  it('calla cuando la contraseña está bien', () => {
    expect(errorDePassword('Segura123')).toBeNull()
  })
})

describe('errorDeConfirmacion', () => {
  it('no reclama antes de que empiece a repetirla', () => {
    expect(errorDeConfirmacion('Segura123', '')).toBeNull()
  })

  it('avisa apenas difieren, y calla cuando coinciden', () => {
    expect(errorDeConfirmacion('Segura123', 'Segura12')).toContain('iguales')
    expect(errorDeConfirmacion('Segura123', 'Segura123')).toBeNull()
  })
})

describe('coherencia entre el texto y la regla', () => {
  it('los requisitos que se muestran mencionan el mínimo real', () => {
    // Si alguien cambia el mínimo, el texto que lee la persona tiene que seguirlo.
    expect(REQUISITOS_PASSWORD).toContain(String(MINIMO_CARACTERES))
  })
})
