import { useCallback, useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { Crosshair, Loader2, MapPin, Search } from 'lucide-react'

import { buscarLugares, describirPunto, lineaDe, PAUSA_DEL_BUSCADOR } from '../../services/lugares'

/**
 * Dónde abrir el mapa cuando todavía no hay nada marcado: el centro de
 * Asunción. Abrirlo en el punto cero del Atlántico no orienta a nadie.
 */
const CENTRO_POR_DEFECTO = { lat: -25.2637, lon: -57.5759 }

/** El servidor de tiles exige identificar la aplicación. */
const ATRIBUCION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'

/**
 * El icono por omisión de Leaflet se arma con rutas relativas que Vite no
 * empaqueta, y el marcador queda invisible. Se dibuja uno propio en SVG: no
 * depende de ningún archivo y se ve igual en cualquier despliegue.
 */
const ICONO = L.divIcon({
  className: '',
  html: `<svg width="32" height="32" viewBox="0 0 24 24" fill="#ef4444" stroke="white" stroke-width="1.5">
    <path d="M12 22s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11z"/>
    <circle cx="12" cy="11" r="2.5" fill="white" stroke="none"/>
  </svg>`,
  iconSize: [32, 32],
  // La punta es lo que señala, no el centro: sin esto el marcador apunta unos
  // metros al norte de lo que se eligió.
  iconAnchor: [16, 32],
})

/**
 * Marcar una dirección de entrega en el mapa.
 *
 * Reemplaza al formulario donde había que escribir «Latitud» y «Longitud» a
 * mano —nadie las sabe de memoria, así que quedaban vacías—. El punto se elige
 * tocando el mapa, buscando la calle o dejando que el navegador ubique al
 * visitante, y el texto de la dirección se deduce de ese punto.
 *
 * @param {{lat:number, lon:number}|null} punto  Lo marcado hasta ahora.
 * @param {(punto:{lat:number, lon:number}) => void} onPunto
 * @param {(detectada:object) => void} onDireccion  La calle deducida del punto.
 */
export default function MapaDeDireccion({ punto, onPunto, onDireccion }) {
  const contenedor = useRef(null)
  const mapa = useRef(null)
  const marcador = useRef(null)
  const espera = useRef(null)
  const [consulta, setConsulta] = useState('')
  const [resultados, setResultados] = useState([])
  const [detectada, setDetectada] = useState(null)
  const [detectando, setDetectando] = useState(false)
  const [ubicando, setUbicando] = useState(false)
  const [aviso, setAviso] = useState(null)

  const marcar = useCallback(async (destino, { mover = false } = {}) => {
    onPunto(destino)
    setResultados([])
    if (mover && mapa.current) mapa.current.setView([destino.lat, destino.lon], 17)
    setDetectando(true)
    const encontrada = await describirPunto(destino)
    setDetectada(encontrada)
    setDetectando(false)
    onDireccion(encontrada)
  }, [onDireccion, onPunto])

  // El mapa se crea una sola vez; después solo se le mueve el marcador.
  useEffect(() => {
    if (mapa.current || !contenedor.current) return undefined
    const inicial = punto ?? CENTRO_POR_DEFECTO
    mapa.current = L.map(contenedor.current).setView([inicial.lat, inicial.lon], punto ? 17 : 13)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: ATRIBUCION,
    }).addTo(mapa.current)
    mapa.current.on('click', (evento) => {
      void marcar({ lat: evento.latlng.lat, lon: evento.latlng.lng })
    })
    return () => {
      mapa.current?.remove()
      mapa.current = null
    }
    // Se monta una sola vez a propósito: volver a crearlo perdería el
    // desplazamiento y el acercamiento que la persona ya hizo.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // El marcador sigue al punto elegido, venga de donde venga.
  useEffect(() => {
    if (!mapa.current) return
    if (!punto) {
      marcador.current?.remove()
      marcador.current = null
      return
    }
    if (marcador.current) marcador.current.setLatLng([punto.lat, punto.lon])
    else marcador.current = L.marker([punto.lat, punto.lon], { icon: ICONO }).addTo(mapa.current)
  }, [punto])

  const escribir = (valor) => {
    setConsulta(valor)
    window.clearTimeout(espera.current)
    if (valor.trim().length < 3) {
      setResultados([])
      return
    }
    espera.current = window.setTimeout(async () => {
      setResultados(await buscarLugares(valor))
    }, PAUSA_DEL_BUSCADOR)
  }

  const ubicarme = () => {
    if (!navigator.geolocation) {
      setAviso('Este navegador no sabe dónde estás. Marcá el punto en el mapa.')
      return
    }
    setUbicando(true)
    setAviso(null)
    navigator.geolocation.getCurrentPosition(
      (posicion) => {
        setUbicando(false)
        void marcar(
          { lat: posicion.coords.latitude, lon: posicion.coords.longitude },
          { mover: true },
        )
      },
      () => {
        setUbicando(false)
        setAviso('No pudimos ubicarte. Podés marcar el punto tocando el mapa.')
      },
      { enableHighAccuracy: true, timeout: 12_000 },
    )
  }

  const linea = detectada ? lineaDe(detectada) : null

  return (
    <div className="space-y-2">
      <div className="relative">
        <div className="flex items-center gap-2 rounded-lg border border-neutral-700 bg-neutral-900 px-3 py-2">
          <Search size={16} className="text-neutral-muted" />
          <input
            type="text"
            value={consulta}
            onChange={(event) => escribir(event.target.value)}
            placeholder="Buscar una calle o un lugar"
            className="w-full bg-transparent text-sm text-neutral-text outline-none"
          />
        </div>
        {resultados.length > 0 && (
          <ul className="absolute z-[1000] mt-1 max-h-52 w-full overflow-auto rounded-lg border border-neutral-700 bg-neutral-900 shadow-lg">
            {resultados.map((lugar) => (
              <li key={`${lugar.lat},${lugar.lon}`}>
                <button
                  type="button"
                  onClick={() => {
                    setConsulta('')
                    void marcar({ lat: lugar.lat, lon: lugar.lon }, { mover: true })
                  }}
                  className="flex w-full items-start gap-2 px-3 py-2 text-left text-sm text-neutral-secondary hover:bg-neutral-800"
                >
                  <MapPin size={14} className="mt-0.5 shrink-0" />
                  <span>{lugar.nombre}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="relative">
        <div ref={contenedor} className="h-72 w-full rounded-lg" />
        <button
          type="button"
          onClick={ubicarme}
          disabled={ubicando}
          title="Usar mi ubicación"
          className="absolute bottom-3 right-3 z-[1000] rounded-full bg-neutral-900 p-2 text-neutral-text shadow-lg hover:bg-neutral-800 disabled:opacity-60"
        >
          {ubicando ? <Loader2 size={18} className="animate-spin" /> : <Crosshair size={18} />}
        </button>
      </div>

      <p className="text-xs text-neutral-muted">
        Tocá el mapa donde te llevamos el pedido. La calle la buscamos nosotros.
      </p>

      <div className="rounded-lg bg-neutral-800/60 px-3 py-2 text-sm">
        {!punto && <span className="text-neutral-muted">Todavía no marcaste el punto.</span>}
        {punto && detectando && <span className="text-neutral-secondary">Buscando la dirección…</span>}
        {punto && !detectando && (
          <>
            <span className="text-neutral-text">
              {linea ?? 'Sin nombre de calle por acá. La ubicación queda igual: el reparto va por el mapa.'}
            </span>
            <span className="ml-2 whitespace-nowrap font-mono text-xs text-neutral-muted">
              {punto.lat.toFixed(6)}, {punto.lon.toFixed(6)}
            </span>
          </>
        )}
      </div>

      {aviso && <p className="text-xs text-accent-400">{aviso}</p>}
    </div>
  )
}
