import { describe, expect, it } from 'vitest'
import { formatPrecio } from './format'

describe('formatPrecio', () => {
  it('usa guaraníes por defecto y respeta monedas con decimales', () => {
    expect(formatPrecio(150_000)).toBe(
      new Intl.NumberFormat('es-PY', { style: 'currency', currency: 'PYG' }).format(150_000),
    )
    expect(formatPrecio(12.5, 'USD')).toBe(
      new Intl.NumberFormat('es-PY', { style: 'currency', currency: 'USD' }).format(12.5),
    )
  })

  it('representa valores ausentes sin convertirlos a cero', () => {
    expect(formatPrecio(null)).toBe('—')
    expect(formatPrecio(undefined)).toBe('—')
  })
})
