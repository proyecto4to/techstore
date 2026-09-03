import { useEffect, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { Search, Plus, Trash2, Printer } from 'lucide-react'
import { productoService } from '../services/productoService'
import { ventaService } from '../services/ventaService'
import { facturaService } from '../services/facturaService'
import { empresaService } from '../services/empresaService'
import { formatPrecio } from '../utils/format'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import AdminTabs from '../components/admin/AdminTabs'
import CobroPanel from '../components/admin/CobroPanel'

/**
 * Venta en mostrador: el cliente vino a la tienda sin pedido web. Se arma la
 * venta, se cobra y se emite la factura en un solo paso.
 *
 * Está pensada para trabajar sin sacar las manos del teclado: se escribe, la
 * lista se filtra sola y Enter agrega el primer resultado. Con el cliente
 * esperando de pie, cada clic de más se siente.
 */
function AdminVentaDirectaPage() {
  const [texto, setTexto] = useState('')
  const [carrito, setCarrito] = useState([])
  const [emailCliente, setEmailCliente] = useState('')
  const [ultimaVenta, setUltimaVenta] = useState(null)
  const [error, setError] = useState(null)
  const buscador = useRef(null)
  const queryClient = useQueryClient()

  // Se busca mientras escribe: antes había que apretar Enter o el botón para
  // que la lista se moviera, un paso de más en cada artículo de cada venta.
  const busqueda = useDebouncedValue(texto.trim())

  // El foco arranca en la búsqueda y vuelve ahí después de cada alta, así se
  // pueden cargar varios artículos seguidos sin tocar el mouse.
  useEffect(() => {
    buscador.current?.focus()
  }, [])

  const { data: productos, isLoading, isError, refetch } = useQuery({
    queryKey: ['venta-directa-productos', busqueda],
    queryFn: () => productoService.listarParaOperar({ search: busqueda, size: 8 }),
    placeholderData: keepPreviousData,
  })

  const disponibles = (productos?.content ?? []).filter((p) => p.stockDisponible > 0)

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
    // Se limpia y se vuelve al buscador: el siguiente artículo se carga
    // escribiendo, sin tener que volver al campo con el mouse.
    setTexto('')
    buscador.current?.focus()
  }

  /**
   * Enter carga el primer resultado con stock.
   *
   * Es el atajo que convierte cada artículo en «escribo tres letras y Enter».
   * Si hay varios candidatos igual toma el primero, que es el que la búsqueda
   * ya puso arriba; para elegir otro está la lista.
   */
  const agregarPrimerResultado = (event) => {
    event.preventDefault()
    const primero = disponibles[0]
    if (!primero) {
      setError(
        texto.trim()
          ? `No hay productos con stock que coincidan con "${texto.trim()}".`
          : 'Escribí el nombre de un producto para buscarlo.',
      )
      return
    }
    agregar(primero)
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

  /** La rebaja debe dejar un importe positivo para poder facturar y cobrar. */
  const cambiarDescuento = (productoId, descuento) => {
    setCarrito((actual) =>
      actual.map((i) =>
        i.productoId === productoId
          ? {
              ...i,
              descuento:
                descuento === ''
                  ? ''
                  : Math.max(0, Math.min(Number(descuento), Math.max(0, Number(i.precio) - 1))),
            }
          : i,
      ),
    )
  }

  const quitar = (productoId) =>
    setCarrito((actual) => actual.filter((i) => i.productoId !== productoId))

  /**
   * El correo del cliente viaja dentro del receptor, junto con el RUC si lo hay.
   * Si no se informa, la venta de mostrador queda correctamente como "solo imprimir".
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
          <form onSubmit={agregarPrimerResultado} className="mb-2">
            <div className="relative">
              <Search
                size={18}
                aria-hidden="true"
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-neutral-muted"
              />
              <input
                ref={buscador}
                value={texto}
                onChange={(e) => {
                  setTexto(e.target.value)
                  setError(null)
                }}
                placeholder="Escribí el nombre del producto…"
                className="input w-full pl-10"
                aria-label="Buscar productos"
              />
            </div>
          </form>
          <p className="mb-4 text-xs text-neutral-muted">
            La lista se filtra mientras escribís. Enter agrega el primero.
          </p>

          {isLoading && <p className="text-neutral-muted">Buscando…</p>}
          {isError && (
            <p className="alerta-error text-sm" role="alert">
              No pudimos buscar productos.{' '}
              <button type="button" onClick={refetch} className="underline">
                Reintentar
              </button>
            </p>
          )}
          {!isLoading && productos && productos.content.length === 0 && (
            <p className="text-neutral-muted">
              {busqueda
                ? `No hay productos que coincidan con "${busqueda}".`
                : 'No hay productos cargados.'}
            </p>
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
              {/* El scroll queda dentro de la tabla: sin esto, en una pantalla
                  angosta la página entera se corre de costado. */}
              <div className="mb-4 overflow-x-auto">
              <table className="tabla-admin tabla-apilable">
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
                      <td data-label="Producto" className="text-neutral-text">
                        {i.nombre}
                      </td>
                      <td data-label="Cantidad">
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
                      <td data-label="Descuento c/u">
                        <input
                          type="number"
                          min="0"
                          max={Math.max(0, Number(i.precio) - 1)}
                          step="1"
                          value={i.descuento}
                          placeholder="0"
                          onChange={(e) => cambiarDescuento(i.productoId, e.target.value)}
                          className="input w-24 py-1 text-sm"
                          aria-label={`Descuento por unidad de ${i.nombre}`}
                        />
                      </td>
                      <td data-label="Subtotal" className="text-primary-300">
                        {formatPrecio(subtotalDe(i))}
                      </td>
                      <td className="justify-end">
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
              </div>

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
                razonSocialObligatoria
                permitirGuardarEnCliente={false}
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
