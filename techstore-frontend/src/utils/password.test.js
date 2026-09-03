import { describe, expect, it } from 'vitest'
import { passwordValida, passwordsCoinciden } from './password'

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
})
