import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Trash2, PartyPopper } from 'lucide-react'
import { maximoComprable, productoDisponible, useCartStore, selectTotalMonto } from '../store/cartStore'
import { useAuthStore } from '../store/authStore'
import { pedidoService } from '../services/pedidoService'
import { formatPrecio } from '../utils/format'
import { checkoutService } from '../services/checkoutService'

const DIRECCION_VACIA = {
  nombreDestinatario: '', telefono: '', departamento: '', ciudad: '', barrio: '',
  direccionLinea1: '', numeroCasa: '', referencia: '', latitud: '', longitud: '', principal: true,
}

function CartPage() {
  const { items, setCantidad, quitar, vaciar } = useCartStore()
  const total = useCartStore(selectTotalMonto)
  const { usuario } = useAuthStore()
  const navigate = useNavigate()

  const [notas, setNotas] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const [pedidoCreado, setPedidoCreado] = useState(null)
  const [metodoEntrega, setMetodoEntrega] = useState('RETIRO_TIENDA')
  const [metodoPago, setMetodoPago] = useState('PAGO_EN_LOCAL')
  const [direcciones, setDirecciones] = useState([])
  const [direccionId, setDireccionId] = useState('')
  const [cotizaciones, setCotizaciones] = useState([])
  const [tarifaId, setTarifaId] = useState('')
  const [cargandoEnvio, setCargandoEnvio] = useState(false)
  const [mostrarDireccion, setMostrarDireccion] = useState(false)
  const [direccionForm, setDireccionForm] = useState(DIRECCION_VACIA)
  const intentoCheckout = useRef(null)
  const hayProductoSinStock = items.some(({ producto }) => !productoDisponible(producto))

  useEffect(() => {
    if (!usuario || metodoEntrega !== 'ENVIO_DOMICILIO') return
    let active = true
    checkoutService.direcciones().then((data) => {
      if (!active) return
      setDirecciones(data)
      const principal = data.find((direccion) => direccion.principal) ?? data[0]
      setDireccionId((current) => current || (principal ? String(principal.id) : ''))
    }).catch(() => active && setError('No se pudieron cargar tus direcciones.'))
    return () => { active = false }
  }, [usuario, metodoEntrega])

  useEffect(() => {
    if (metodoEntrega !== 'ENVIO_DOMICILIO' || !direccionId) {
      setCotizaciones([])
      setTarifaId('')
      return
    }
    let active = true
    setCargandoEnvio(true)
    checkoutService.cotizar(Number(direccionId), items)
      .then((data) => {
        if (!active) return
        setCotizaciones(data)
        setTarifaId(data[0]?.tarifaId ? String(data[0].tarifaId) : '')
      })
      .catch(() => active && setError('No se pudo calcular el envío para esa dirección.'))
      .finally(() => active && setCargandoEnvio(false))
    return () => { active = false }
  }, [direccionId, metodoEntrega, items])

  const cambiarEntrega = (value) => {
    setMetodoEntrega(value)
    if (value === 'ENVIO_DOMICILIO' && metodoPago === 'PAGO_EN_LOCAL') {
      setMetodoPago('PAGO_EFECTIVO_REPARTIDOR')
    }
    if (value === 'RETIRO_TIENDA' && metodoPago === 'PAGO_EFECTIVO_REPARTIDOR') {
      setMetodoPago('PAGO_EN_LOCAL')
    }
  }

  const handleCheckout = async () => {
    if (hayProductoSinStock) {
      setError('Quitá del carrito los productos agotados antes de finalizar la compra.')
      return
    }
    if (!usuario) {
      navigate('/login')
      return
    }
    if (metodoEntrega === 'ENVIO_DOMICILIO' && (!direccionId || !tarifaId)) {
      setError('Seleccioná una dirección y una modalidad de envío antes de continuar.')
      return
    }
    setError(null)
    setEnviando(true)
    try {
      const firma = JSON.stringify({
        items: items.map(({ producto, cantidad }) => [producto.id, cantidad]).sort(([a], [b]) => a - b),
        notas: notas || null,
        metodoEntrega,
        metodoPago,
        direccionId: metodoEntrega === 'ENVIO_DOMICILIO' ? Number(direccionId) : null,
        tarifaId: metodoEntrega === 'ENVIO_DOMICILIO' ? Number(tarifaId) : null,
      })
      if (intentoCheckout.current?.firma !== firma) {
        intentoCheckout.current = { firma, clave: crypto.randomUUID() }
      }
      const pedido = await pedidoService.checkout(items, notas, intentoCheckout.current.clave, {
        metodoEntrega,
        metodoPago,
        direccionEntregaId: metodoEntrega === 'ENVIO_DOMICILIO' ? Number(direccionId) : null,
        tarifaEnvioId: metodoEntrega === 'ENVIO_DOMICILIO' ? Number(tarifaId) : null,
      })
      setPedidoCreado(pedido)
      intentoCheckout.current = null
      vaciar()
    } catch (err) {
      setError(err.response?.data?.detail ?? 'No se pudo completar la compra. Intente de nuevo.')
    } finally {
      setEnviando(false)
    }
  }

  const guardarDireccion = async (event) => {
    event.preventDefault()
    setError(null)
    setCargandoEnvio(true)
    try {
      const creada = await checkoutService.crearDireccion({
        ...direccionForm,
        barrio: direccionForm.barrio.trim() || null,
        numeroCasa: direccionForm.numeroCasa.trim() || null,
        referencia: direccionForm.referencia.trim() || null,
        latitud: Number(direccionForm.latitud),
        longitud: Number(direccionForm.longitud),
      })
      setDirecciones((current) => [...current, creada])
      setDireccionId(String(creada.id))
      setDireccionForm(DIRECCION_VACIA)
      setMostrarDireccion(false)
    } catch (err) {
      setError(err.response?.data?.detail ?? 'No se pudo guardar la dirección.')
    } finally {
      setCargandoEnvio(false)
    }
  }

  if (pedidoCreado) {
    return (
      <div className="container mx-auto px-6 py-8 text-center">
        <div className="card mx-auto max-w-lg p-8">
          <PartyPopper className="mx-auto mb-4 text-accent-400" size={40} />
          <h2 className="mb-4 text-3xl font-bold text-neutral-text">¡Pedido registrado!</h2>
          <p className="mb-2 text-neutral-secondary">
            Tu pedido <span className="font-bold text-primary-300">N.º {pedidoCreado.numero}</span>{' '}
            fue creado por un total de{' '}
            <span className="font-bold text-neutral-text">{formatPrecio(pedidoCreado.total)}</span>.
          </p>
          <p className="mb-6 text-sm text-neutral-muted">
            Estado: Pendiente — el stock quedó reservado a tu nombre.
          </p>
          <div className="flex justify-center gap-4">
            <Link to="/pedidos" className="btn btn-primary">
              Ver mis pedidos
            </Link>
            <Link to="/" className="btn btn-secondary">
              Seguir comprando
            </Link>
          </div>
        </div>
      </div>
    )
  }

  if (items.length === 0) {
    return (
      <div className="container mx-auto px-6 py-16 text-center">
        <h2 className="mb-4 text-3xl font-bold text-neutral-text">Tu carrito está vacío</h2>
        <Link to="/" className="text-primary-300 underline hover:text-accent-400">
          Ir al catálogo
        </Link>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">Carrito</h1>

      {error && (
        <p className="alerta-error" role="alert">
          {error}
        </p>
      )}
      {hayProductoSinStock && (
        <p className="alerta-error" role="alert">
          Hay productos agotados en el carrito. Quitalos para poder continuar.
        </p>
      )}

      <div className="card overflow-x-auto p-0">
        <table className="tabla-admin">
          <thead>
            <tr>
              <th>Producto</th>
              <th>Precio</th>
              <th>Cantidad</th>
              <th>Subtotal</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {items.map(({ producto, cantidad }) => {
              const maximo = maximoComprable(producto)
              return <tr key={producto.id}>
                <td className="font-medium text-neutral-text">{producto.nombre}</td>
                <td>{formatPrecio(producto.precio)}</td>
                <td>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setCantidad(producto.id, cantidad - 1)}
                      disabled={cantidad <= 1}
                      title={cantidad <= 1 ? 'Mínimo 1 — usá el tacho para quitarlo' : undefined}
                      className="h-7 w-7 rounded border border-dark-border text-neutral-text hover:border-primary-400 hover:bg-primary-400/10 disabled:cursor-not-allowed disabled:opacity-40"
                      aria-label={`Restar uno de ${producto.nombre}`}
                    >
                      −
                    </button>
                    <span className="w-8 text-center">{cantidad}</span>
                    <button
                      onClick={() => setCantidad(producto.id, cantidad + 1)}
                      disabled={cantidad >= maximo}
                      title={cantidad >= maximo ? 'Límite por línea alcanzado' : undefined}
                      className="h-7 w-7 rounded border border-dark-border text-neutral-text hover:border-primary-400 hover:bg-primary-400/10 disabled:cursor-not-allowed disabled:opacity-40"
                      aria-label={`Sumar uno de ${producto.nombre}`}
                    >
                      +
                    </button>
                  </div>
                  <p className={`mt-1 text-xs ${cantidad >= maximo ? 'text-orange-400' : 'text-neutral-muted'}`}>
                    {cantidad >= maximo
                      ? `Máximo por línea: ${maximo}`
                      : 'Stock y precio se revalidan al finalizar'}
                  </p>
                </td>
                <td className="font-semibold text-primary-300">
                  {formatPrecio(producto.precio * cantidad)}
                </td>
                <td>
                  <button
                    onClick={() => quitar(producto.id)}
                    className="text-red-400 hover:text-red-300"
                    aria-label={`Quitar ${producto.nombre}`}
                  >
                    <Trash2 size={18} />
                  </button>
                </td>
              </tr>
            })}
          </tbody>
        </table>
      </div>

      <section className="card mt-6 space-y-5">
        <h2 className="text-xl font-semibold text-neutral-text">Entrega y pago</h2>
        <div className="grid gap-4 md:grid-cols-2">
          <label className="block text-sm font-medium text-neutral-text">
            Modalidad de entrega
            <select value={metodoEntrega} onChange={(event) => cambiarEntrega(event.target.value)} className="input mt-2">
              <option value="RETIRO_TIENDA">Retiro en el local</option>
              <option value="ENVIO_DOMICILIO">Envío a domicilio</option>
            </select>
          </label>
          <label className="block text-sm font-medium text-neutral-text">
            Forma de pago
            <select value={metodoPago} onChange={(event) => setMetodoPago(event.target.value)} className="input mt-2">
              {metodoEntrega === 'RETIRO_TIENDA' ? (
                <option value="PAGO_EN_LOCAL">Pago en el local</option>
              ) : (
                <option value="PAGO_EFECTIVO_REPARTIDOR">Efectivo al repartidor</option>
              )}
              <option value="TRANSFERENCIA_BANCARIA">Transferencia bancaria</option>
            </select>
          </label>
        </div>

        {metodoEntrega === 'ENVIO_DOMICILIO' && (
          <div className="space-y-4 rounded-md border border-dark-border p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h3 className="font-semibold text-neutral-text">Dirección de entrega</h3>
              <button type="button" onClick={() => setMostrarDireccion((value) => !value)} className="btn btn-secondary text-sm">
                {mostrarDireccion ? 'Cancelar' : 'Nueva dirección'}
              </button>
            </div>
            {direcciones.length > 0 && (
              <select value={direccionId} onChange={(event) => setDireccionId(event.target.value)} className="input">
                {direcciones.map((direccion) => (
                  <option key={direccion.id} value={direccion.id}>
                    {direccion.direccionLinea1}{direccion.numeroCasa ? ` ${direccion.numeroCasa}` : ''} · {direccion.ciudad}
                  </option>
                ))}
              </select>
            )}
            {direcciones.length === 0 && !mostrarDireccion && (
              <p className="text-sm text-orange-400">Agregá una dirección para poder calcular el envío.</p>
            )}
            {mostrarDireccion && (
              <form onSubmit={guardarDireccion} className="grid gap-3 md:grid-cols-2">
                {[
                  ['nombreDestinatario', 'Nombre del destinatario'], ['telefono', 'Teléfono'],
                  ['departamento', 'Departamento'], ['ciudad', 'Ciudad'], ['barrio', 'Barrio'],
                  ['direccionLinea1', 'Calle / dirección'], ['numeroCasa', 'Número de casa'],
                  ['referencia', 'Referencia'], ['latitud', 'Latitud para la ruta'], ['longitud', 'Longitud para la ruta'],
                ].map(([key, label]) => (
                  <label key={key} className="block text-sm font-medium text-neutral-text">
                    {label}{['nombreDestinatario', 'telefono', 'departamento', 'ciudad', 'direccionLinea1', 'latitud', 'longitud'].includes(key) ? ' *' : ''}
                    <input
                      required={['nombreDestinatario', 'telefono', 'departamento', 'ciudad', 'direccionLinea1', 'latitud', 'longitud'].includes(key)}
                      type={['latitud', 'longitud'].includes(key) ? 'number' : 'text'}
                      step={['latitud', 'longitud'].includes(key) ? 'any' : undefined}
                      value={direccionForm[key]}
                      onChange={(event) => setDireccionForm({ ...direccionForm, [key]: event.target.value })}
                      className="input mt-2"
                    />
                  </label>
                ))}
                <button type="submit" disabled={cargandoEnvio} className="btn btn-primary md:col-span-2">
                  Guardar y usar dirección
                </button>
              </form>
            )}
            {cargandoEnvio && <p className="text-sm text-neutral-muted">Calculando alternativas…</p>}
            {cotizaciones.length > 0 && (
              <fieldset>
                <legend className="mb-2 text-sm font-medium text-neutral-text">Modalidad de envío</legend>
                <div className="grid gap-2 md:grid-cols-2">
                  {cotizaciones.map((cotizacion) => (
                    <label key={cotizacion.tarifaId} className={`cursor-pointer rounded-md border p-3 ${String(cotizacion.tarifaId) === tarifaId ? 'border-primary-400 bg-primary-400/10' : 'border-dark-border'}`}>
                      <input type="radio" name="tarifa" value={cotizacion.tarifaId}
                        checked={String(cotizacion.tarifaId) === tarifaId}
                        onChange={(event) => setTarifaId(event.target.value)} className="mr-2" />
                      <strong className="text-neutral-text">{cotizacion.nombre}</strong>
                      <span className="ml-2 text-primary-300">{formatPrecio(cotizacion.costo)}</span>
                      <span className="mt-1 block text-xs text-neutral-muted">
                        {cotizacion.fechaEstimadaDesde ? `Estimado ${cotizacion.fechaEstimadaDesde} a ${cotizacion.fechaEstimadaHasta}` : cotizacion.descripcion}
                      </span>
                    </label>
                  ))}
                </div>
              </fieldset>
            )}
          </div>
        )}
      </section>

      <div className="mt-6 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <textarea
          value={notas}
          onChange={(e) => setNotas(e.target.value)}
          placeholder="Notas para el pedido (opcional)"
          rows={3}
          className="input md:max-w-md"
        />
        <div className="text-right">
          <p className="mb-3 text-2xl font-bold text-neutral-text">
            Total: <span className="gradient-text">{formatPrecio(
              total + (cotizaciones.find((quote) => String(quote.tarifaId) === tarifaId)?.costo ?? 0),
            )}</span>
          </p>
          <button
            onClick={handleCheckout}
            disabled={enviando || hayProductoSinStock}
            title={
              hayProductoSinStock
                ? 'Quitá los productos agotados antes de continuar'
                : enviando
                  ? 'Procesando tu pedido…'
                  : undefined
            }
            className="btn btn-primary px-8 py-3 disabled:cursor-not-allowed"
          >
            {enviando ? 'Procesando…' : usuario ? 'Finalizar compra' : 'Ingresar para comprar'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default CartPage
