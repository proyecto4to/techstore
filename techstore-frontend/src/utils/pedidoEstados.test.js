import { describe, expect, it } from 'vitest'
import {
  estadoEntrega,
  estadoEnvio,
  estadoPedido,
  estadoVehiculo,
  eventoVehiculo,
  humanizarCodigo,
} from './pedidoEstados'

describe('estadoPedido', () => {
  it('resuelve los estados conocidos', () => {
    expect(estadoPedido('G')).toMatchObject({ label: 'Pagado' })
    expect(estadoPedido('P')).toMatchObject({ label: 'Pendiente' })
  })

  it('vuelve legible un estado que todavía no está traducido', () => {
    // Antes devolvía el código tal cual, así que un estado nuevo en el backend
    // le aparecía al cliente como "ALGO_NUEVO" en la pantalla de su pedido.
    expect(estadoPedido('ALGO_NUEVO').label).toBe('Algo nuevo')
  })
})

describe('estadoEntrega y estadoEnvio', () => {
  it('traducen los estados de la entrega a domicilio', () => {
    expect(estadoEntrega('EN_RUTA')).toMatchObject({ label: 'En camino' })
    expect(estadoEntrega('NO_ENTREGADO')).toMatchObject({ label: 'No se pudo entregar' })
  })

  it('traducen los estados del envío', () => {
    expect(estadoEnvio('LISTO_PARA_ENVIO')).toMatchObject({ label: 'Listo para despachar' })
    expect(estadoEnvio('INTENTO_ENTREGA')).toMatchObject({ label: 'Intentamos entregarlo' })
  })
})

describe('estadoVehiculo y eventoVehiculo', () => {
  it('traducen los estados de la flota', () => {
    expect(estadoVehiculo('FUERA_DE_SERVICIO')).toMatchObject({ label: 'Fuera de servicio' })
    expect(estadoVehiculo('EN_USO')).toMatchObject({ label: 'En uso' })
  })

  it('traducen lo que le pasó al vehículo', () => {
    expect(eventoVehiculo('RECARGA_COMBUSTIBLE')).toBe('Carga de combustible')
    expect(eventoVehiculo('ESTANCAMIENTO')).toBe('Quedó varado')
  })
})

describe('humanizarCodigo', () => {
  it('convierte un identificador interno en algo legible', () => {
    expect(humanizarCodigo('EN_DESTINO')).toBe('En destino')
    expect(humanizarCodigo('')).toBe('Sin estado')
    expect(humanizarCodigo(null)).toBe('Sin estado')
  })
})
