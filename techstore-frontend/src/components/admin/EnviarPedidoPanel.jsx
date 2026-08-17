import { useState } from 'react'
import { Mail, MessageCircle, Send } from 'lucide-react'

/**
 * Los tres canales que se pidieron. WhatsApp figura pero está deshabilitado a
 * propósito: mandar en nombre de un negocio exige una cuenta de WhatsApp
 * Business API, y mostrar la opción como si anduviera haría que alguien crea
 * que el proveedor recibió un mensaje que nunca salió.
 */
const CANALES = [
  {
    valor: 'Correo',
    etiqueta: 'Correo electrónico',
    icono: Mail,
    disponible: true,
  },
  {
    valor: 'WhatsApp',
    etiqueta: 'WhatsApp',
    icono: MessageCircle,
    disponible: false,
  },
  {
    valor: 'Ambos',
    etiqueta: 'Correo y WhatsApp',
    icono: Send,
    disponible: false,
  },
]

function EnviarPedidoPanel({ compra, ocupado, onEnviar, onCancelar }) {
  const [canal, setCanal] = useState('Correo')
  const sinCorreo = !compra.proveedorEmail

  return (
    <div>
      <h4 className="mb-1 text-sm font-semibold text-neutral-text">
        Mandarle el pedido N.º {compra.numero} a {compra.proveedorRazonSocial}
      </h4>
      <p className="mb-4 text-xs text-neutral-muted">
        Le llega el detalle de los productos, las cantidades y el total. La compra sigue Pendiente:
        mandar el pedido no es que el proveedor lo haya aceptado.
      </p>

      <div className="mb-4 flex flex-wrap gap-3">
        {CANALES.map((c) => {
          const Icono = c.icono
          const activo = canal === c.valor
          return (
            <button
              key={c.valor}
              type="button"
              disabled={!c.disponible}
              onClick={() => setCanal(c.valor)}
              title={c.disponible ? undefined : 'Necesita una cuenta de WhatsApp Business API'}
              className={`inline-flex items-center gap-2 rounded-md border px-4 py-2 text-sm transition-all ${
                !c.disponible
                  ? 'cursor-not-allowed border-dark-border text-neutral-muted opacity-50'
                  : activo
                    ? 'border-primary-400 bg-primary-400/15 font-semibold text-primary-300'
                    : 'border-dark-border text-neutral-secondary hover:border-primary-400/50'
              }`}
            >
              <Icono size={15} /> {c.etiqueta}
            </button>
          )
        })}
      </div>

      <p className="mb-4 text-xs text-neutral-muted">
        WhatsApp todavía no está disponible: hace falta una cuenta de <strong>WhatsApp Business
        API</strong> (Meta Cloud API o Twilio), con verificación del negocio y costo por
        conversación. Un WhatsApp común no sirve para esto.
      </p>

      {sinCorreo ? (
        <p className="alerta-error">
          {compra.proveedorRazonSocial} no tiene correo cargado. Completalo en la ficha del
          proveedor para poder mandarle el pedido.
        </p>
      ) : (
        <p className="mb-4 text-sm text-neutral-secondary">
          Destino: <span className="font-mono text-neutral-text">{compra.proveedorEmail}</span>
        </p>
      )}

      <div className="flex gap-2">
        <button
          onClick={() => onEnviar(canal)}
          disabled={ocupado || sinCorreo}
          className="btn btn-primary inline-flex items-center gap-2 text-sm"
        >
          <Send size={14} /> {ocupado ? 'Enviando…' : 'Enviar el pedido'}
        </button>
        <button onClick={onCancelar} className="btn btn-secondary text-sm">
          Cancelar
        </button>
      </div>
    </div>
  )
}

export default EnviarPedidoPanel
