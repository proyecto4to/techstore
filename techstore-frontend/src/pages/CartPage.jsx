import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Trash2, PartyPopper } from 'lucide-react'
import {
  maximoComprable,
  muestraUnidadesRestantes,
  productoDisponible,
  useCartStore,
  selectTotalMonto,
} from '../store/cartStore'
import { useAuthStore } from '../store/authStore'
import { pedidoService } from '../services/pedidoService'
import { formatPrecio, formatRangoEntrega } from '../utils/format'
import { checkoutService } from '../services/checkoutService'
import { pagoService } from '../services/pagoService'
import MapaDeDireccion from '../components/common/MapaDeDireccion'

/**
 * Lo que hace falta escribir de una direccion.
 *
 * Antes eran doce campos, dos de ellos «Latitud» y «Longitud» a mano. Ahora el
 * punto se marca en el mapa y la calle se deduce de ese punto: queda solo lo
 * que ningun mapa sabe.
 */
const DIRECCION_VACIA = {
  descripcion: '', nombreDestinatario: '', telefono: '', referencia: '', principal: true,
}

/** Como se lee una direccion en la lista: por su nombre, no por su calle. */
const nombreDeDireccion = (direccion) => {
  const calle = [direccion.direccionLinea1, direccion.numeroCasa].filter(Boolean).join(' ')
  const lugar = [calle, direccion.ciudad].filter(Boolean).join(', ')
  if (direccion.descripcion) return lugar ? direccion.descripcion + ' · ' + lugar : direccion.descripcion
  return lugar || 'Ubicacion marcada en el mapa'
}

function CartPage() {
  const { items, setCantidad, quitar, vaciar, aplicarDisponibilidad } = useCartStore()
  const total = useCartStore(selectTotalMonto)
  const { usuario } = useAuthStore()
  const navigate = useNavigate()

  const [avisoStock, setAvisoStock] = useState(null)
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
  // El punto marcado y la direccion que se dedujo de el. Van aparte del
  // formulario porque no los escribe nadie: los pone el mapa.
  const [punto, setPunto] = useState(null)
  const [detectada, setDetectada] = useState(null)
  const intentoCheckout = useRef(null)
  // Transferencia: los datos de la tienda y los comprobantes que el cliente ya
  // subió para esta compra. Se suben apenas se eligen; el checkout los usa.
  const [transferencia, setTransferencia] = useState(null)
  const [qrUrl, setQrUrl] = useState(null)
  const [comprobantes, setComprobantes] = useState([])
  const [subiendo, setSubiendo] = useState(false)
  const porTransferencia = metodoPago === 'TRANSFERENCIA_BANCARIA'
  const hayProductoSinStock = items.some(({ producto }) => !productoDisponible(producto))

  useEffect(() => {
    if (!usuario || !porTransferencia || transferencia) return
    let activo = true
    pagoService.datosTransferencia()
      .then((datos) => {
        if (!activo) return
        setTransferencia(datos)
        if (datos.tieneQr) pagoService.urlDelQr().then((url) => activo && setQrUrl(url)).catch(() => {})
      })
      .catch(() => activo && setError('No pudimos traer los datos para transferir.'))
    return () => { activo = false }
  }, [usuario, porTransferencia, transferencia])

  useEffect(() => () => { if (qrUrl) URL.revokeObjectURL(qrUrl) }, [qrUrl])

  const subirComprobante = async (event) => {
    const archivo = event.target.files?.[0]
    event.target.value = ''
    if (!archivo) return
    if (archivo.size > 5 * 1024 * 1024) {
      setError('El comprobante pesa más de 5 MB. Probá con una captura de pantalla o el PDF del banco.')
      return
    }
    setSubiendo(true)
    setError(null)
    try {
      const subido = await pagoService.subirComprobante(archivo)
      setComprobantes((actuales) => [...actuales, { ...subido, nombre: archivo.name }])
    } catch (err) {
      setError(err.response?.data?.detail ?? 'No pudimos subir el comprobante. Intentá de nuevo.')
    } finally {
      setSubiendo(false)
    }
  }

  const quitarComprobante = async (id) => {
    setComprobantes((actuales) => actuales.filter((c) => c.id !== id))
    pagoService.quitarComprobante(id).catch(() => {})
  }

  // El stock pudo cambiar desde que se agregaron las cosas. Se revisa al
  // abrir el carrito para mostrarlo ya corregido, en vez de que el cliente se
  // entere recién cuando el checkout le rechaza la compra.
  //
  // Corre una sola vez al montar: depender de `items` lo volvería a disparar
  // con cada ajuste de cantidad, incluidos los que hace esta misma revisión.
  const revisado = useRef(false)
  useEffect(() => {
    if (revisado.current) return
    revisado.current = true
    const actuales = useCartStore.getState().items
    if (actuales.length === 0) return
    checkoutService
      .disponibilidad(actuales)
      .then(({ topePorProducto }) => {
        const reporte = aplicarDisponibilidad(topePorProducto)
        if (reporte.huboCambios) setAvisoStock(reporte.mensaje)
      })
      // Sin conexión el carrito guardado sigue sirviendo: no se molesta al
      // cliente acá, porque el checkout vuelve a validar antes de cobrar.
      .catch(() => {})
  }, [aplicarDisponibilidad])

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

  // Un envío a domicilio se paga antes, por transferencia: es la regla del
  // negocio y la valida el servidor. El efectivo al repartidor ya no existe.
  const cambiarEntrega = (value) => {
    setMetodoEntrega(value)
    if (value === 'ENVIO_DOMICILIO') setMetodoPago('TRANSFERENCIA_BANCARIA')
  }

  const costoEnvio = cotizaciones.find((quote) => String(quote.tarifaId) === tarifaId)?.costo ?? 0
  const totalAPagar = total + (metodoEntrega === 'ENVIO_DOMICILIO' ? costoEnvio : 0)

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
    if (porTransferencia && comprobantes.length === 0) {
      setError('Adjuntá el comprobante de la transferencia antes de confirmar la compra.')
      return
    }
    setError(null)
    setAvisoStock(null)
    setEnviando(true)
    try {
      // Última revisión antes de comprometer stock: entre que armó el carrito
      // y este momento pudo cambiar la disponibilidad.
      const { valido, topePorProducto } = await checkoutService.disponibilidad(items)
      if (!valido) {
        const reporte = aplicarDisponibilidad(topePorProducto)
        setAvisoStock(
          reporte.huboCambios
            ? reporte.mensaje
            : 'La disponibilidad de tu carrito cambió. Revisá las cantidades antes de continuar.',
        )
        return
      }

      const firma = JSON.stringify({
        items: items.map(({ producto, cantidad }) => [producto.id, cantidad]).sort(([a], [b]) => a - b),
        notas: notas || null,
        metodoEntrega,
        metodoPago,
        direccionId: metodoEntrega === 'ENVIO_DOMICILIO' ? Number(direccionId) : null,
        tarifaId: metodoEntrega === 'ENVIO_DOMICILIO' ? Number(tarifaId) : null,
        comprobantes: porTransferencia ? comprobantes.map((c) => c.id).sort() : [],
      })
      if (intentoCheckout.current?.firma !== firma) {
        intentoCheckout.current = { firma, clave: crypto.randomUUID() }
      }
      const pedido = await pedidoService.checkout(items, notas, intentoCheckout.current.clave, {
        metodoEntrega,
        metodoPago,
        direccionEntregaId: metodoEntrega === 'ENVIO_DOMICILIO' ? Number(direccionId) : null,
        tarifaEnvioId: metodoEntrega === 'ENVIO_DOMICILIO' ? Number(tarifaId) : null,
        comprobanteIds: porTransferencia ? comprobantes.map((c) => c.id) : [],
        // Lo que se le mostró y transfirió: si el servidor calcula otro total,
        // rechaza la compra en vez de crear un pedido que el comprobante no cubre.
        totalEsperado: porTransferencia ? totalAPagar : null,
      })
      setPedidoCreado(pedido)
      intentoCheckout.current = null
      setComprobantes([])
      vaciar()
    } catch (err) {
      setError(err.response?.data?.detail ?? 'No se pudo completar la compra. Intente de nuevo.')
    } finally {
      setEnviando(false)
    }
  }

  const guardarDireccion = async (event) => {
    event.preventDefault()
    if (!punto) {
      setError('Marca en el mapa donde te llevamos el pedido.')
      return
    }
    setError(null)
    setCargandoEnvio(true)
    try {
      const creada = await checkoutService.crearDireccion({
        descripcion: direccionForm.descripcion.trim() || null,
        nombreDestinatario: direccionForm.nombreDestinatario.trim(),
        telefono: direccionForm.telefono.trim(),
        referencia: direccionForm.referencia.trim() || null,
        // La calle sale del punto marcado, no de lo que alguien escriba.
        departamento: detectada?.departamento ?? null,
        ciudad: detectada?.ciudad ?? null,
        barrio: detectada?.barrio ?? null,
        direccionLinea1: detectada?.calle ?? null,
        numeroCasa: detectada?.numeroCasa ?? null,
        codigoPostal: detectada?.codigoPostal ?? null,
        latitud: punto.lat,
        longitud: punto.lon,
        principal: direccionForm.principal,
      })
      setDirecciones((current) => [...current, creada])
      setDireccionId(String(creada.id))
      setDireccionForm(DIRECCION_VACIA)
      setPunto(null)
      setDetectada(null)
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
            {pedidoCreado.estadoPago === 'EN_VERIFICACION'
              ? 'Estamos verificando tu transferencia. Te avisamos apenas la confirmemos y empezamos a prepararlo.'
              : 'Estado: Pendiente — el stock quedó reservado a tu nombre.'}
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
      <h1 className="mb-2 text-4xl font-bold text-neutral-text">Finalizar compra</h1>
      {/* Los tres pasos están a la vista en la misma página, pero numerados:
          en una pantalla grande verlo todo junto es una ventaja, y el número
          igual dice dónde está parado y qué falta. */}
      <p className="mb-6 text-sm text-neutral-secondary">
        Revisá lo que llevás, elegí cómo lo recibís y confirmá. Son tres pasos.
      </p>

      <h2 className="mb-3 text-xl font-semibold text-neutral-text">
        <span className="mr-2 text-primary-300">1.</span>
        Lo que llevás
      </h2>

      {error && (
        <p className="alerta-error" role="alert">
          {error}
        </p>
      )}
      {avisoStock && (
        <p
          role="status"
          className="mb-4 rounded-md border border-amber-400/40 bg-amber-400/10 px-4 py-3 text-sm text-amber-300"
        >
          {avisoStock}
        </p>
      )}
      {hayProductoSinStock && (
        <p className="alerta-error" role="alert">
          Hay productos agotados en el carrito. Quitalos para poder continuar.
        </p>
      )}

      <div className="card overflow-x-auto p-0">
        <table className="tabla-admin tabla-apilable">
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
                <td data-label="Producto" className="font-medium text-neutral-text">
                  {producto.nombre}
                </td>
                <td data-label="Precio">{formatPrecio(producto.precio)}</td>
                <td data-label="Cantidad" className="flex-col items-start">
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
                      title={
                        cantidad >= maximo
                          ? 'No hay más unidades disponibles'
                          : `Sumar uno de ${producto.nombre}`
                      }
                      className="h-7 w-7 rounded border border-dark-border text-neutral-text hover:border-primary-400 hover:bg-primary-400/10 disabled:cursor-not-allowed disabled:opacity-40"
                      aria-label={`Sumar uno de ${producto.nombre}`}
                    >
                      +
                    </button>
                  </div>
                  {/* El número solo se dice cuando refleja escasez real: con
                      stock alto el tope es un límite por pedido, no lo que
                      queda en góndola. */}
                  {cantidad >= maximo && (
                    <p className="mt-1 text-xs text-orange-400">
                      {muestraUnidadesRestantes(producto)
                        ? maximo === 1
                          ? 'Queda 1 unidad disponible'
                          : `Quedan ${maximo} unidades disponibles`
                        : `Podés llevar hasta ${maximo} por pedido`}
                    </p>
                  )}
                </td>
                <td data-label="Subtotal" className="font-semibold text-primary-300">
                  {formatPrecio(producto.precio * cantidad)}
                </td>
                <td className="justify-end">
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
        <h2 className="text-xl font-semibold text-neutral-text">
          <span className="mr-2 text-primary-300">2.</span>
          Cómo lo recibís y cómo lo pagás
        </h2>
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
              {metodoEntrega === 'RETIRO_TIENDA' && (
                <option value="PAGO_EN_LOCAL">Pago en el local</option>
              )}
              <option value="TRANSFERENCIA_BANCARIA">Transferencia bancaria</option>
            </select>
            {metodoEntrega === 'ENVIO_DOMICILIO' && (
              <span className="mt-1 block text-xs text-neutral-muted">
                Los envíos a domicilio se pagan por adelantado. Preparamos tu pedido al verificar la transferencia.
              </span>
            )}
          </label>
        </div>

        {porTransferencia && usuario && (
          <div className="space-y-4 rounded-md border border-dark-border p-4">
            {!transferencia ? (
              <p className="text-sm text-neutral-muted">Trayendo los datos para transferir…</p>
            ) : !transferencia.disponible ? (
              <p className="alerta-error">
                La tienda todavía no habilitó sus datos para transferir.
                {metodoEntrega === 'RETIRO_TIENDA' ? ' Elegí pagar en el local.' : ' Por ahora solo se puede retirar en el local.'}
              </p>
            ) : (
              <>
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="space-y-1 text-sm">
                    <p className="text-neutral-muted">Transferí exactamente</p>
                    <p className="text-2xl font-bold text-neutral-text">{formatPrecio(totalAPagar)}</p>
                    {[
                      ['Banco', transferencia.banco],
                      ['Titular', transferencia.titular],
                      ['RUC o CI', transferencia.documento],
                      ['Tipo de cuenta', transferencia.tipoCuenta],
                      ['N.º de cuenta', transferencia.numeroCuenta],
                      ['Alias', transferencia.alias],
                    ].filter(([, valor]) => valor).map(([etiqueta, valor]) => (
                      <p key={etiqueta} className="text-neutral-secondary">
                        {etiqueta}: <span className="font-semibold text-neutral-text">{valor}</span>{' '}
                        <button type="button" className="text-xs text-primary-300 underline"
                          onClick={() => navigator.clipboard?.writeText(valor)}>copiar</button>
                      </p>
                    ))}
                    {transferencia.instrucciones && (
                      <p className="text-xs text-neutral-muted">{transferencia.instrucciones}</p>
                    )}
                  </div>
                  {qrUrl && (
                    <img src={qrUrl} alt="QR para transferir" className="h-40 w-40 rounded bg-white p-2" />
                  )}
                </div>
                <div>
                  <p className="mb-2 font-semibold text-neutral-text">Comprobante de la transferencia</p>
                  {comprobantes.length === 0 && (
                    <p className="mb-2 text-sm text-neutral-muted">
                      Cuando hayas transferido, adjuntá la captura o el PDF del comprobante. Sin él no podemos confirmar tu compra.
                    </p>
                  )}
                  <ul className="mb-2 space-y-1 text-sm">
                    {comprobantes.map((c) => (
                      <li key={c.id} className="flex items-center justify-between gap-2 text-neutral-secondary">
                        <span>✓ {c.nombre}</span>
                        <button type="button" className="text-xs text-red-400 underline" onClick={() => quitarComprobante(c.id)}>
                          Quitar
                        </button>
                      </li>
                    ))}
                  </ul>
                  {comprobantes.length < 3 && (
                    <label className="btn btn-secondary inline-block cursor-pointer text-sm">
                      {subiendo ? 'Subiendo…' : comprobantes.length === 0 ? 'Adjuntar comprobante' : 'Adjuntar otro'}
                      <input type="file" accept="image/jpeg,image/png,application/pdf" className="hidden"
                        disabled={subiendo} onChange={subirComprobante} />
                    </label>
                  )}
                </div>
              </>
            )}
          </div>
        )}

        {metodoEntrega === 'ENVIO_DOMICILIO' && (
          <div className="space-y-4 rounded-md border border-dark-border p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h3 className="font-semibold text-neutral-text">Dirección de entrega</h3>
              <button type="button" onClick={() => setMostrarDireccion((value) => !value)} className="btn btn-secondary text-sm">
                {mostrarDireccion ? 'Cancelar' : 'Nueva dirección'}
              </button>
            </div>
            {direcciones.length > 0 && (
              <select value={direccionId} onChange={(event) => setDireccionId(event.target.value)} className="input" aria-label="Dirección de entrega">
                {direcciones.map((direccion) => (
                  <option key={direccion.id} value={direccion.id}>
                    {nombreDeDireccion(direccion)}
                  </option>
                ))}
              </select>
            )}
            {direcciones.length === 0 && !mostrarDireccion && (
              <p className="text-sm text-orange-400">Agregá una dirección para poder calcular el envío.</p>
            )}
            {mostrarDireccion && (
              <form onSubmit={guardarDireccion} className="space-y-4">
                <MapaDeDireccion punto={punto} onPunto={setPunto} onDireccion={setDetectada} />
                <div className="grid gap-3 md:grid-cols-2">
                  <label className="block text-sm font-medium text-neutral-text">
                    Como le decis a este lugar
                    <input
                      type="text"
                      maxLength={60}
                      placeholder="Casa, Trabajo…"
                      value={direccionForm.descripcion}
                      onChange={(event) => setDireccionForm({ ...direccionForm, descripcion: event.target.value })}
                      className="input mt-2"
                    />
                  </label>
                  <label className="block text-sm font-medium text-neutral-text">
                    Referencia para encontrarlo
                    <input
                      type="text"
                      maxLength={300}
                      placeholder="Porton negro, al lado de la despensa"
                      value={direccionForm.referencia}
                      onChange={(event) => setDireccionForm({ ...direccionForm, referencia: event.target.value })}
                      className="input mt-2"
                    />
                  </label>
                  <label className="block text-sm font-medium text-neutral-text">
                    Nombre de quien recibe *
                    <input
                      required
                      type="text"
                      maxLength={120}
                      value={direccionForm.nombreDestinatario}
                      onChange={(event) => setDireccionForm({ ...direccionForm, nombreDestinatario: event.target.value })}
                      className="input mt-2"
                    />
                  </label>
                  <label className="block text-sm font-medium text-neutral-text">
                    Telefono de contacto *
                    <input
                      required
                      type="tel"
                      maxLength={30}
                      value={direccionForm.telefono}
                      onChange={(event) => setDireccionForm({ ...direccionForm, telefono: event.target.value })}
                      className="input mt-2"
                    />
                  </label>
                </div>
                <button type="submit" disabled={cargandoEnvio || !punto} className="btn btn-primary w-full">
                  {punto ? 'Guardar y usar direccion' : 'Marca el punto en el mapa'}
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
                        {cotizacion.fechaEstimadaDesde
                          ? formatRangoEntrega(
                              cotizacion.fechaEstimadaDesde,
                              cotizacion.fechaEstimadaHasta,
                            )
                          : cotizacion.descripcion}
                      </span>
                    </label>
                  ))}
                </div>
              </fieldset>
            )}
          </div>
        )}
      </section>

      <h2 className="mb-3 mt-8 text-xl font-semibold text-neutral-text">
        <span className="mr-2 text-primary-300">3.</span>
        Confirmá tu compra
      </h2>

      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <textarea
          value={notas}
          onChange={(e) => setNotas(e.target.value)}
          placeholder="Por ejemplo, una referencia para llegar"
          rows={3}
          className="input md:max-w-md"
          aria-label="Aclaraciones para el pedido"
        />
        <div className="text-right">
          <p className="mb-3 text-2xl font-bold text-neutral-text">
            Total: <span className="gradient-text">{formatPrecio(totalAPagar)}</span>
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
