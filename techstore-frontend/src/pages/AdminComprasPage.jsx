import { Fragment, useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { ChevronDown, ChevronUp, PackagePlus, Truck, Wallet } from 'lucide-react'
import { proveedorService, compraService } from '../services/compraService'
import { productoService } from '../services/productoService'
import { formatPrecio } from '../utils/format'
import { estadoPedido } from '../utils/pedidoEstados'
import AdminTabs from '../components/admin/AdminTabs'
import PedidoDetallePanel from '../components/admin/PedidoDetallePanel'

const PROVEEDOR_VACIO = { razonSocial: '', ruc: '', telefono: '', email: '' }

const ETIQUETA_COMPRA = {
  P: { label: 'Pendiente', clase: 'bg-yellow-400/20 text-yellow-400' },
  C: { label: 'Recibida', clase: 'bg-emerald-400/20 text-emerald-400' },
  X: { label: 'Anulada', clase: 'bg-neutral-muted/20 text-neutral-muted' },
}

function AdminComprasPage() {
  const [page, setPage] = useState(0)
  const [proveedorId, setProveedorId] = useState('')
  const [items, setItems] = useState([])
  const [productoId, setProductoId] = useState('')
  const [cantidad, setCantidad] = useState(1)
  const [costo, setCosto] = useState('')
  const [notas, setNotas] = useState('')
  const [mostrarNuevoProveedor, setMostrarNuevoProveedor] = useState(false)
  const [nuevoProveedor, setNuevoProveedor] = useState(PROVEEDOR_VACIO)
  const [detalleAbierto, setDetalleAbierto] = useState(null)
  const [medioPago, setMedioPago] = useState({})
  const [mensaje, setMensaje] = useState(null)
  const [error, setError] = useState(null)
  const queryClient = useQueryClient()

  const { data: proveedores = [] } = useQuery({
    queryKey: ['proveedores-activos'],
    queryFn: proveedorService.activos,
  })

  const { data: productos } = useQuery({
    queryKey: ['productos-para-compra'],
    queryFn: () => productoService.listar({ size: 100, incluirInactivos: true }),
  })

  const { data: compras, isLoading } = useQuery({
    queryKey: ['admin-compras', page],
    queryFn: () => compraService.listar({ page }),
    placeholderData: keepPreviousData,
  })

  const refrescar = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-compras'] })
    queryClient.invalidateQueries({ queryKey: ['productos'] })
    queryClient.invalidateQueries({ queryKey: ['admin-productos'] })
    queryClient.invalidateQueries({ queryKey: ['caja-resumen'] })
    queryClient.invalidateQueries({ queryKey: ['caja-movimientos'] })
  }

  const onError = (err) => {
    const errores = err.response?.data?.errores
    setError(
      errores
        ? Object.values(errores).join(' · ')
        : err.response?.data?.detail ?? 'La operación falló.',
    )
  }

  const crearProveedorMutation = useMutation({
    mutationFn: (p) => proveedorService.crear(p),
    onSuccess: (p) => {
      setError(null)
      setMensaje(`Proveedor "${p.razonSocial}" creado.`)
      setNuevoProveedor(PROVEEDOR_VACIO)
      setMostrarNuevoProveedor(false)
      setProveedorId(String(p.id))
      queryClient.invalidateQueries({ queryKey: ['proveedores-activos'] })
    },
    onError,
  })

  const crearCompraMutation = useMutation({
    mutationFn: () =>
      compraService.crear({
        proveedorId: Number(proveedorId),
        items: items.map((i) => ({
          productoId: i.producto.id,
          cantidad: i.cantidad,
          costoUnitario: Number(i.costo),
        })),
        notas: notas.trim() || null,
      }),
    onSuccess: (compra) => {
      setError(null)
      setMensaje(`Compra N.º ${compra.numero} registrada (${formatPrecio(compra.total)}). Recibila para sumar el stock.`)
      setItems([])
      setNotas('')
      refrescar()
    },
    onError,
  })

  const recibirMutation = useMutation({
    mutationFn: (id) => compraService.recibir(id),
    onSuccess: (compra) => {
      setError(null)
      setMensaje(`Compra N.º ${compra.numero} recibida: stock actualizado.`)
      refrescar()
    },
    onError,
  })

  const pagarMutation = useMutation({
    mutationFn: ({ id, medio }) => compraService.pagar(id, medio),
    onSuccess: (compra) => {
      setError(null)
      setMensaje(`Compra N.º ${compra.numero} pagada (${formatPrecio(compra.total)}): egreso registrado en caja.`)
      refrescar()
    },
    onError,
  })

  const anularMutation = useMutation({
    mutationFn: (id) => compraService.anular(id),
    onSuccess: (compra) => {
      setError(null)
      setMensaje(`Compra N.º ${compra.numero} anulada.`)
      refrescar()
    },
    onError,
  })

  const agregarItem = () => {
    const producto = productos?.content.find((p) => p.id === Number(productoId))
    if (!producto || !costo || Number(costo) <= 0 || items.some((i) => i.producto.id === producto.id)) return
    setItems([...items, { producto, cantidad: Math.max(1, Number(cantidad)), costo }])
    setProductoId('')
    setCantidad(1)
    setCosto('')
  }

  const totalCompra = items.reduce((acc, i) => acc + Number(i.costo) * i.cantidad, 0)

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Compras</span>
      </h1>
      <AdminTabs />

      {mensaje && <p className="alerta-ok" role="status">{mensaje}</p>}
      {error && <p className="alerta-error" role="alert">{error}</p>}

      <div className="card mb-8">
        <h3 className="mb-4 inline-flex items-center gap-2 text-lg font-semibold text-neutral-text">
          <PackagePlus size={18} className="text-accent-400" /> Nueva compra a proveedor
        </h3>

        <div className="mb-4 flex flex-wrap items-end gap-3">
          <div className="min-w-56 flex-1">
            <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="proveedor">
              Proveedor *
            </label>
            <select id="proveedor" value={proveedorId} onChange={(e) => setProveedorId(e.target.value)} className="input">
              <option value="">Elegir proveedor…</option>
              {proveedores.map((p) => (
                <option key={p.id} value={p.id}>{p.razonSocial}{p.ruc ? ` (${p.ruc})` : ''}</option>
              ))}
            </select>
          </div>
          <button onClick={() => setMostrarNuevoProveedor(!mostrarNuevoProveedor)} className="btn btn-secondary text-sm">
            {mostrarNuevoProveedor ? 'Cancelar' : '+ Nuevo proveedor'}
          </button>
        </div>

        {mostrarNuevoProveedor && (
          <div className="mb-4 grid grid-cols-1 gap-3 rounded-md border border-dark-border bg-dark-bg/40 p-4 md:grid-cols-4">
            <input placeholder="Razón social *" value={nuevoProveedor.razonSocial}
              onChange={(e) => setNuevoProveedor({ ...nuevoProveedor, razonSocial: e.target.value })} className="input" />
            <input placeholder="RUC" value={nuevoProveedor.ruc}
              onChange={(e) => setNuevoProveedor({ ...nuevoProveedor, ruc: e.target.value })} className="input" />
            <input placeholder="Teléfono" value={nuevoProveedor.telefono}
              onChange={(e) => setNuevoProveedor({ ...nuevoProveedor, telefono: e.target.value })} className="input" />
            <div className="flex gap-2">
              <input placeholder="Email" type="email" value={nuevoProveedor.email}
                onChange={(e) => setNuevoProveedor({ ...nuevoProveedor, email: e.target.value })} className="input" />
              <button
                onClick={() => crearProveedorMutation.mutate({ ...nuevoProveedor, razonSocial: nuevoProveedor.razonSocial.trim() })}
                disabled={!nuevoProveedor.razonSocial.trim() || crearProveedorMutation.isPending}
                className="btn btn-primary whitespace-nowrap text-sm"
              >
                Guardar
              </button>
            </div>
          </div>
        )}

        <div className="mb-4 flex flex-wrap items-end gap-3">
          <div className="min-w-56 flex-1">
            <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="cproducto">
              Producto
            </label>
            <select id="cproducto" value={productoId} onChange={(e) => setProductoId(e.target.value)} className="input">
              <option value="">Elegir producto…</option>
              {productos?.content.map((p) => (
                <option key={p.id} value={p.id}>{p.nombre} (stock: {p.stockReal})</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="ccant">
              Cantidad
            </label>
            <input id="ccant" type="number" min="1" value={cantidad}
              onChange={(e) => setCantidad(e.target.value)} className="input w-24" />
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="ccosto">
              Costo unit. (₲)
            </label>
            <input id="ccosto" type="number" min="1" value={costo}
              onChange={(e) => setCosto(e.target.value)} className="input w-36" />
          </div>
          <button onClick={agregarItem} disabled={!productoId || !costo} className="btn btn-secondary">
            Agregar item
          </button>
        </div>

        {items.length > 0 && (
          <div className="mb-4 overflow-x-auto rounded-md border border-dark-border">
            <table className="tabla-admin">
              <thead>
                <tr><th>Producto</th><th>Cant.</th><th>Costo unit.</th><th className="text-right">Subtotal</th><th></th></tr>
              </thead>
              <tbody>
                {items.map((i) => (
                  <tr key={i.producto.id}>
                    <td className="text-neutral-text">{i.producto.nombre}</td>
                    <td>{i.cantidad}</td>
                    <td>{formatPrecio(i.costo)}</td>
                    <td className="text-right font-semibold text-primary-300">{formatPrecio(Number(i.costo) * i.cantidad)}</td>
                    <td>
                      <button onClick={() => setItems(items.filter((x) => x.producto.id !== i.producto.id))}
                        className="text-red-400 hover:text-red-300 text-sm">Quitar</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="flex flex-wrap items-center gap-4">
          <input placeholder="Notas (opcional)" value={notas} onChange={(e) => setNotas(e.target.value)}
            maxLength={1000} className="input max-w-md flex-1" />
          <p className="text-lg font-bold text-neutral-text">
            Total: <span className="gradient-text">{formatPrecio(totalCompra)}</span>
          </p>
          <button
            onClick={() => crearCompraMutation.mutate()}
            disabled={!proveedorId || items.length === 0 || crearCompraMutation.isPending}
            title={
              !proveedorId
                ? 'Elegí un proveedor'
                : items.length === 0
                  ? 'Agregá al menos un item a la compra'
                  : undefined
            }
            className="btn btn-primary disabled:cursor-not-allowed"
          >
            {crearCompraMutation.isPending ? 'Registrando…' : 'Registrar compra'}
          </button>
        </div>
      </div>

      {isLoading && <p className="text-neutral-muted">Cargando compras…</p>}
      {compras && compras.content.length === 0 && <p className="text-neutral-muted">No hay compras registradas.</p>}

      {compras && compras.content.length > 0 && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin">
            <thead>
              <tr>
                <th>N.º</th>
                <th>Fecha</th>
                <th>Proveedor</th>
                <th>Estado</th>
                <th>Total</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {compras.content.map((compra) => {
                const abierto = detalleAbierto === compra.id
                const etiqueta = ETIQUETA_COMPRA[compra.estado] ?? estadoPedido(compra.estado)
                return (
                  <Fragment key={compra.id}>
                    <tr>
                      <td className="font-semibold text-neutral-text">
                        <button
                          onClick={() => setDetalleAbierto(abierto ? null : compra.id)}
                          className="inline-flex items-center gap-1 text-neutral-text transition-colors hover:text-primary-300"
                          aria-expanded={abierto}
                        >
                          {abierto ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                          #{compra.numero}
                        </button>
                      </td>
                      <td>{new Date(compra.fecha).toLocaleString('es-PY')}</td>
                      <td className="text-neutral-secondary">{compra.proveedorRazonSocial ?? '—'}</td>
                      <td>
                        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${etiqueta.clase}`}>
                          {etiqueta.label}
                        </span>
                      </td>
                      <td className="font-semibold text-primary-300">{formatPrecio(compra.total)}</td>
                      <td>
                        <div className="flex flex-wrap items-center gap-2">
                          {compra.estado === 'P' && (
                            <button
                              onClick={() => recibirMutation.mutate(compra.id)}
                              disabled={recibirMutation.isPending}
                              className="btn btn-primary inline-flex items-center gap-2 text-sm"
                            >
                              <Truck size={14} /> Recibir
                            </button>
                          )}
                          {compra.estado === 'C' && (
                            <span className="text-sm text-emerald-400">Stock ✓</span>
                          )}

                          {compra.pagada ? (
                            <span className="inline-flex items-center gap-1 text-sm text-emerald-400">
                              <Wallet size={14} /> Pagada ✓
                            </span>
                          ) : compra.estado !== 'X' ? (
                            <span className="inline-flex items-center gap-1">
                              <select
                                value={medioPago[compra.id] ?? 'Efectivo'}
                                onChange={(e) => setMedioPago({ ...medioPago, [compra.id]: e.target.value })}
                                className="input py-1 text-sm"
                                aria-label="Medio de pago de la compra"
                              >
                                <option value="Efectivo">Efectivo</option>
                                <option value="Transferencia">Transferencia</option>
                              </select>
                              <button
                                onClick={() =>
                                  pagarMutation.mutate({ id: compra.id, medio: medioPago[compra.id] ?? 'Efectivo' })
                                }
                                disabled={pagarMutation.isPending}
                                className="btn btn-secondary inline-flex items-center gap-1 text-sm"
                              >
                                <Wallet size={14} /> Pagar
                              </button>
                            </span>
                          ) : null}

                          {compra.estado === 'P' && !compra.pagada && (
                            <button
                              onClick={() => anularMutation.mutate(compra.id)}
                              disabled={anularMutation.isPending}
                              className="btn btn-danger text-sm"
                            >
                              Anular
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                    {abierto && (
                      <tr>
                        <td colSpan={6} className="bg-dark-bg/30 p-3">
                          <PedidoDetallePanel pedidoId={compra.id} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {compras && compras.totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="btn btn-secondary">
            ← Anterior
          </button>
          <span className="text-neutral-secondary">Página {compras.page + 1} de {compras.totalPages}</span>
          <button onClick={() => setPage((p) => Math.min(compras.totalPages - 1, p + 1))} disabled={page >= compras.totalPages - 1} className="btn btn-secondary">
            Siguiente →
          </button>
        </div>
      )}

      <p className="mt-6 text-sm text-neutral-muted">
        Flujo: registrás la compra (queda Pendiente, sin tocar stock) →{' '}
        <strong className="text-neutral-secondary">Recibir</strong> suma las cantidades al stock real →{' '}
        <strong className="text-neutral-secondary">Pagar</strong> registra el egreso al proveedor en la caja
        (elegí Efectivo o Transferencia). Recibir y Pagar son independientes.
      </p>
    </div>
  )
}

export default AdminComprasPage
