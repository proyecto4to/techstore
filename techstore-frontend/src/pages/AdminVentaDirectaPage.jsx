import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { Search, Plus, Trash2, Printer } from 'lucide-react'
import { productoService } from '../services/productoService'
import { ventaService } from '../services/ventaService'
import { facturaService } from '../services/facturaService'
import { empresaService } from '../services/empresaService'
import { formatPrecio } from '../utils/format'
import AdminTabs from '../components/admin/AdminTabs'
import CobroPanel from '../components/admin/CobroPanel'

/**
 * Venta en mostrador: el cliente vino a la tienda sin pedido web. Se arma la
 * venta, se cobra y se emite la factura en un solo paso.
 */
function AdminVentaDirectaPage() {
  const [busqueda, setBusqueda] = useState('')
  const [texto, setTexto] = useState('')
  const [carrito, setCarrito] = useState([])
  const [emailCliente, setEmailCliente] = useState('')
  const [ultimaVenta, setUltimaVenta] = useState(null)
  const [error, setError] = useState(null)
  const queryClient = useQueryClient()

  const { data: productos, isLoading } = useQuery({
    queryKey: ['venta-directa-productos', busqueda],
    queryFn: () => productoService.listar({ search: busqueda, size: 8 }),
    placeholderData: keepPreviousData,
  })

  // Si a la empresa le faltan datos, la factura no se puede emitir. Se avisa
  // acá arriba en vez de dejar que el error aparezca al final del cobro, con
  // el cliente esperando en el mostrador.
  const { data: pendientes } = useQuery({
    queryKey: ['empresa-pendientes'],
    queryFn: empresaService.pendientes,
  })

  // El descuento es por unidad, igual que lo guarda el sistema y lo pide el
  // documento electrónico: la línea vale (precio − descuento) × cantidad.
  const subtotalDe = (i) => (Number(i.precio) - Number(i.descuento || 0)) * i.cantidad
  const total = carrito.reduce((suma, i) => suma + subtotalDe(i), 0)
  const totalDescuento = carrito.reduce((suma, i) => suma + Number(i.descuento || 0) * i.cantidad, 0)

  const agregar = (producto) => {
    setError(null)
    setCarrito((actual) => {
      const existente = actual.find((i) => i.productoId === producto.id)
      const enCarrito = existente?.cantidad ?? 0
      if (enCarrito + 1 > producto.stockDisponible) {
        setError(`Solo quedan ${producto.stockDisponible} unidades de "${producto.nombre}".`)
        return actual
      }
      if (existente) {
        return actual.map((i) =>
          i.productoId === producto.id ? { ...i, cantidad: i.cantidad + 1 } : i,
        )
      }
      return [
        ...actual,
        {
          productoId: producto.id,
          nombre: producto.nombre,
          precio: producto.precio,
          cantidad: 1,
          descuento: '',
          stockDisponible: producto.stockDisponible,
        },
      ]
    })
  }

  const cambiarCantidad = (productoId, cantidad) => {
    setCarrito((actual) =>
      actual.map((i) =>
        i.productoId === productoId
          ? { ...i, cantidad: Math.max(1, Math.min(cantidad, i.stockDisponible)) }
          : i,
      ),
    )
  }

  /** La rebaja no puede superar el precio: dejaría la línea en negativo. */
  const cambiarDescuento = (productoId, descuento) => {
    setCarrito((actual) =>
      actual.map((i) =>
        i.productoId === productoId
          ? { ...i, descuento: descuento === '' ? '' : Math.max(0, Math.min(Number(descuento), Number(i.precio))) }
          : i,
      ),
    )
  }

  const quitar = (productoId) =>
    setCarrito((actual) => actual.filter((i) => i.productoId !== productoId))

  /**
   * El correo del cliente viaja dentro del receptor, junto con el RUC si lo hay.
   * Como el pedido queda a nombre del cajero, sin esto la factura le llegaría a él.
   */
  const conEmail = (receptor) => {
    const email = emailCliente.trim()
    if (!email) return receptor
    return { ...(receptor ?? { ruc: null, razonSocial: null, guardarEnCliente: false }), email }
  }

  const venderMutation = useMutation({
    mutationFn: ({ lineas, receptor }) =>
      ventaService.vender({
        items: carrito.map((i) => ({
          productoId: i.productoId,
          cantidad: i.cantidad,
          descuento: i.descuento === '' ? null : Number(i.descuento),
        })),
        lineas,
        receptor: conEmail(receptor),
      }),
    onSuccess: (pago) => {
      setError(null)
      setUltimaVenta(pago)
      setCarrito([])
      setEmailCliente('')
      queryClient.invalidateQueries({ queryKey: ['venta-directa-productos'] })
      queryClient.invalidateQueries({ queryKey: ['admin-productos'] })
      queryClient.invalidateQueries({ queryKey: ['productos'] })
      queryClient.invalidateQueries({ queryKey: ['caja-resumen'] })
      queryClient.invalidateQueries({ queryKey: ['caja-movimientos'] })
      queryClient.invalidateQueries({ queryKey: ['caja-cierre-preview'] })
    },
    onError: (err) => {
      setUltimaVenta(null)
      setError(err.response?.data?.detail ?? 'No se pudo registrar la venta.')
    },
  })

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        🛒 Administración — <span className="gradient-text">Venta directa</span>
      </h1>
      <AdminTabs />

      <p className="mb-4 text-sm text-neutral-secondary">
        Para el cliente que viene a la tienda sin haber hecho un pedido por la web. Se descuenta el
        stock, se cobra y se emite la factura en un solo paso.
      </p>

      {pendientes && !pendientes.completo && (
        <div className="alerta-error" role="alert">
          <p className="font-semibold">No se va a poder emitir la factura.</p>
          <p className="mt-1 text-sm">
            Faltan cargar estos datos de la empresa: {pendientes.faltantes.join(', ')}. Completalos en{' '}
            <a href="/admin/empresa" className="underline">Admin → Empresa</a> antes de vender.
          </p>
        </div>
      )}

      {error && <p className="alerta-error" role="alert">{error}</p>}

      {ultimaVenta && (
        <div className="alerta-ok flex flex-wrap items-center gap-4" role="status">
          <span>
            Venta registrada (pedido N.º {ultimaVenta.pedidoNumero}). Factura{' '}
            <strong>{ultimaVenta.factura.numero}</strong> por {formatPrecio(ultimaVenta.factura.total)}{' '}
            {ultimaVenta.factura.receptorRuc
              ? `a ${ultimaVenta.factura.receptorNombre} (RUC ${ultimaVenta.factura.receptorRuc})`
              : 'a consumidor final'}
            {ultimaVenta.factura.receptorEmail
              ? `. Se le envió a ${ultimaVenta.factura.receptorEmail}.`
              : '.'}
          </span>
          <button
            onClick={() => facturaService.abrirKude(ultimaVenta.factura.id)}
            className="btn btn-secondary inline-flex items-center gap-1 text-sm"
          >
            <Printer size={14} /> Imprimir factura
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="card">
          <h3 className="mb-3 text-lg font-semibold text-neutral-text">1. Elegí los productos</h3>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              setBusqueda(texto.trim())
            }}
            className="mb-4 flex gap-2"
          >
            <input
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              placeholder="Buscar por nombre…"
              className="input flex-1"
              aria-label="Buscar productos"
            />
            <button type="submit" className="btn btn-primary inline-flex items-center gap-1">
              <Search size={16} /> Buscar
            </button>
          </form>

          {isLoading && <p className="text-neutral-muted">Cargando…</p>}
          {productos && productos.content.length === 0 && (
            <p className="text-neutral-muted">No hay productos que coincidan.</p>
          )}
          <ul className="space-y-2">
            {productos?.content.map((p) => (
              <li
                key={p.id}
                className="flex items-center justify-between gap-3 rounded-md border border-dark-border p-3"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium text-neutral-text">{p.nombre}</p>
                  <p className="text-sm text-primary-300">
                    {formatPrecio(p.precio)}{' '}
                    <span className="text-neutral-muted">· {p.stockDisponible} disponibles</span>
                  </p>
                </div>
                <button
                  onClick={() => agregar(p)}
                  disabled={p.stockDisponible < 1}
                  title={p.stockDisponible < 1 ? 'Sin stock disponible' : undefined}
                  className="btn btn-secondary inline-flex shrink-0 items-center gap-1 text-sm disabled:cursor-not-allowed"
                >
                  <Plus size={14} /> Agregar
                </button>
              </li>
            ))}
          </ul>
        </div>

        <div className="card">
          <h3 className="mb-3 text-lg font-semibold text-neutral-text">2. Cobrá</h3>
          {carrito.length === 0 ? (
            <p className="text-neutral-muted">
              Todavía no agregaste productos. Buscá y agregá para empezar la venta.
            </p>
          ) : (
            <>
              <table className="tabla-admin mb-4">
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th>Cant.</th>
                    <th>Desc. c/u</th>
                    <th>Subtotal</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {carrito.map((i) => (
                    <tr key={i.productoId}>
                      <td className="text-neutral-text">{i.nombre}</td>
                      <td>
                        <input
                          type="number"
                          min="1"
                          max={i.stockDisponible}
                          value={i.cantidad}
                          onChange={(e) => cambiarCantidad(i.productoId, Number(e.target.value))}
                          className="input w-20 py-1 text-sm"
                          aria-label={`Cantidad de ${i.nombre}`}
                        />
                      </td>
                      <td>
                        <input
                          type="number"
                          min="0"
                          max={i.precio}
                          step="1"
                          value={i.descuento}
                          placeholder="0"
                          onChange={(e) => cambiarDescuento(i.productoId, e.target.value)}
                          className="input w-24 py-1 text-sm"
                          aria-label={`Descuento por unidad de ${i.nombre}`}
                        />
                      </td>
                      <td className="text-primary-300">{formatPrecio(subtotalDe(i))}</td>
                      <td>
                        <button
                          onClick={() => quitar(i.productoId)}
                          className="btn btn-danger px-2 py-1 text-sm"
                          aria-label={`Quitar ${i.nombre}`}
                        >
                          <Trash2 size={14} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {totalDescuento > 0 && (
                <p className="mb-3 text-sm text-neutral-secondary">
                  Descuento aplicado:{' '}
                  <span className="font-semibold text-amber-400">−{formatPrecio(totalDescuento)}</span>
                </p>
              )}

              <div className="mb-3">
                <label
                  className="mb-1 block text-sm font-medium text-neutral-text"
                  htmlFor="email-cliente"
                >
                  📧 Correo del cliente (opcional)
                </label>
                <input
                  id="email-cliente"
                  type="email"
                  value={emailCliente}
                  onChange={(e) => setEmailCliente(e.target.value)}
                  maxLength={255}
                  placeholder="Para mandarle la factura; si lo dejás vacío, solo se imprime"
                  className="input"
                />
              </div>

              <CobroPanel
                total={total}
                ocupado={venderMutation.isPending}
                onCobrar={(lineas, receptor) => venderMutation.mutate({ lineas, receptor })}
                onCancelar={() => {
                  setCarrito([])
                  setEmailCliente('')
                }}
              />
            </>
          )}
        </div>
      </div>
    </div>
  )
}

export default AdminVentaDirectaPage
