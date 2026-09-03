import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Truck } from 'lucide-react'
import { usuarioService } from '../../services/usuarioService'
import { repartoService } from '../../services/repartoService'
import { useAuthStore } from '../../store/authStore'
import { estadoEntrega } from '../../utils/pedidoEstados'

export default function AsignacionRepartoPanel({ pedido }) {
  const { usuario } = useAuthStore()
  const [repartidorId, setRepartidorId] = useState('')
  const [ordenEntrega, setOrdenEntrega] = useState(1)
  const [observaciones, setObservaciones] = useState('')
  const [mensaje, setMensaje] = useState(null)
  const queryClient = useQueryClient()

  const { data } = useQuery({
    queryKey: ['repartidores-activos'],
    queryFn: () => usuarioService.listar({ page: 0, size: 100 }),
    enabled: usuario?.rol === 'Admin',
  })
  const repartidores = (data?.content ?? []).filter((u) => u.rol === 'Repartidor' && u.activo)
  const { data: asignaciones = [] } = useQuery({
    queryKey: ['asignaciones-reparto', pedido.id],
    queryFn: () => repartoService.asignaciones(pedido.id),
    enabled: usuario?.rol === 'Admin' && pedido.metodoEntrega === 'ENVIO_DOMICILIO',
  })

  const asignar = useMutation({
    mutationFn: () =>
      repartoService.asignar(pedido.id, {
        repartidorId: Number(repartidorId),
        ordenEntrega: Number(ordenEntrega),
        observaciones,
      }),
    onSuccess: (reparto) => {
      setMensaje(`Pedido asignado a ${reparto.repartidor} con orden ${reparto.ordenEntrega}.`)
      queryClient.invalidateQueries({ queryKey: ['admin-pedido', pedido.id] })
      queryClient.invalidateQueries({ queryKey: ['asignaciones-reparto', pedido.id] })
    },
  })

  if (usuario?.rol !== 'Admin' || pedido.metodoEntrega !== 'ENVIO_DOMICILIO') return null
  const asignable = ['C', 'G'].includes(pedido.estado)

  return (
    <section className="mt-4 rounded-md border border-primary-400/30 bg-primary-400/5 p-4">
      {asignable ? <form
        onSubmit={(event) => {
          event.preventDefault()
          asignar.mutate()
        }}
      >
        <h4 className="mb-3 inline-flex items-center gap-2 font-semibold text-neutral-text">
          <Truck size={17} /> Asignar o reasignar entrega
        </h4>
        {mensaje && <p className="alerta-ok text-sm" role="status">{mensaje}</p>}
        {asignar.isError && (
          <p className="alerta-error text-sm" role="alert">
            {asignar.error.response?.data?.detail ?? 'No se pudo asignar el pedido.'}
          </p>
        )}
        <div className="grid gap-3 md:grid-cols-4">
        <select
          required
          value={repartidorId}
          onChange={(event) => setRepartidorId(event.target.value)}
          className="input"
          aria-label="Repartidor"
        >
          <option value="">Seleccionar repartidor</option>
          {repartidores.map((repartidor) => (
            <option key={repartidor.id} value={repartidor.id}>
              {repartidor.nombre} {repartidor.apellido}
            </option>
          ))}
        </select>
        <input
          type="number"
          min="1"
          required
          value={ordenEntrega}
          onChange={(event) => setOrdenEntrega(event.target.value)}
          className="input"
          aria-label="Orden sugerido"
          placeholder="Orden"
        />
        <input
          value={observaciones}
          onChange={(event) => setObservaciones(event.target.value)}
          maxLength={1000}
          className="input"
          placeholder="Observaciones opcionales"
         aria-label="Observaciones opcionales"/>
        <button
          type="submit"
          disabled={!repartidorId || asignar.isPending}
          className="btn btn-primary"
        >
          {asignar.isPending ? 'Asignando…' : 'Asignar entrega'}
        </button>
        </div>
        {repartidores.length === 0 && (
          <p className="mt-2 text-sm text-orange-400">Creá y activá al menos un usuario Repartidor.</p>
        )}
      </form> : (
        <p className="text-sm text-neutral-muted">
          El pedido solo puede asignarse o reasignarse mientras está confirmado o pagado.
        </p>
      )}

      {asignaciones.length > 0 && (
        <div className="mt-4 border-t border-dark-border pt-3">
          <h5 className="mb-2 text-sm font-semibold text-neutral-text">Historial de asignaciones</h5>
          <div className="space-y-2">
            {asignaciones.map((item, index) => (
              <div key={item.id} className="flex flex-wrap justify-between gap-2 text-sm text-neutral-muted">
                <span>
                  {index === 0 && !['CANCELADO', 'ENTREGADO', 'NO_ENTREGADO'].includes(item.estadoEntrega)
                    ? 'Actual: '
                    : ''}
                  <strong className="text-neutral-text">{item.repartidor}</strong>
                  {' · '}orden {item.ordenEntrega} · {estadoEntrega(item.estadoEntrega).label}
                </span>
                <time dateTime={item.fechaAsignacion}>
                  {new Date(item.fechaAsignacion).toLocaleString('es-PY')}
                </time>
                {item.resultadoFinal && <span className="basis-full">Resultado: {item.resultadoFinal}</span>}
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}
