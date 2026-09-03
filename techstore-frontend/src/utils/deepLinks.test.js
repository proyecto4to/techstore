import { describe, expect, it } from 'vitest'

import { rutaDeEnlace } from './deepLinks'

describe('rutaDeEnlace', () => {
  it('lleva al panel de vehículos solo al Admin', () => {
    expect(rutaDeEnlace('techstore://vehiculo/3', 'Admin')).toBe('/admin/vehiculos')
    // El cajero recibe avisos de caja, no de flota; y aunque le llegara uno,
    // ProtectedRoute lo rebotaría al inicio.
    expect(rutaDeEnlace('techstore://vehiculo/3', 'Cajero')).toBeNull()
    expect(rutaDeEnlace('techstore://vehiculo/3', 'Cliente')).toBeNull()
  })

  it('lleva las transferencias a la bandeja del cajero', () => {
    expect(rutaDeEnlace('techstore://transferencia/8', 'Cajero')).toBe('/admin/solicitudes')
    expect(rutaDeEnlace('techstore://transferencia/8', 'Admin')).toBe('/admin/solicitudes')
    expect(rutaDeEnlace('techstore://transferencia/8', 'Cliente')).toBeNull()
  })

  it('manda el chat al panel o al inicio según quién mire', () => {
    expect(rutaDeEnlace('techstore://chat/12', 'Cajero')).toBe('/admin/mensajes')
    expect(rutaDeEnlace('techstore://chat/12', 'Cliente')).toBe('/')
  })

  it('no ofrece destino para las entregas, que se operan desde el celular', () => {
    expect(rutaDeEnlace('techstore://reparto/5', 'Admin')).toBeNull()
    expect(rutaDeEnlace('techstore://reparto/5', 'Repartidor')).toBeNull()
  })

  it('lleva el pedido a la lista, que es lo más cerca que hay en la web', () => {
    expect(rutaDeEnlace('techstore://pedido/70', 'Cliente')).toBe('/pedidos')
  })

  it('conserva el token de la recuperación de contraseña', () => {
    expect(rutaDeEnlace('techstore://app/password/reset?token=a b/c', null)).toBe(
      '/password/reset?token=a%20b%2Fc',
    )
    expect(rutaDeEnlace('techstore://app/otra/cosa', null)).toBeNull()
  })

  it('ignora lo que no sea un enlace nuestro', () => {
    expect(rutaDeEnlace(null, 'Admin')).toBeNull()
    expect(rutaDeEnlace('', 'Admin')).toBeNull()
    expect(rutaDeEnlace('https://techstore.py/admin', 'Admin')).toBeNull()
    expect(rutaDeEnlace('techstore://inventado/1', 'Admin')).toBeNull()
  })
})
