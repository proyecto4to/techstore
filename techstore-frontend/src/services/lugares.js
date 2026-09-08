/**
 * El servicio de direcciones de OpenStreetMap.
 *
 * Busca calles por nombre y deduce la dirección de un punto del mapa. Va por
 * `fetch` pelado y no por el cliente de la aplicación a propósito: ese lleva el
 * token de sesión en cada pedido y no hay razón para mandárselo a un tercero.
 * El mapa ya le pide sus tiles al mismo servidor, así que no se le cuenta nada
 * nuevo a nadie.
 *
 * Es la contraparte de `DirectorioDeLugares` de la aplicación móvil: mismas
 * claves, mismo país, mismas reglas.
 */
const BASE = 'https://nominatim.openstreetmap.org'

/** Paraguay: acota la búsqueda a donde la tienda entrega. */
const PAIS = 'py'

/** El servicio pide no pasar de una consulta por segundo. */
export const PAUSA_DEL_BUSCADOR = 600

const texto = (valor) => {
  const limpio = valor?.toString().trim()
  return limpio ? limpio : null
}

async function consultar(ruta, parametros) {
  const url = new URL(`${BASE}${ruta}`)
  for (const [clave, valor] of Object.entries(parametros)) {
    url.searchParams.set(clave, valor)
  }
  const respuesta = await fetch(url, { headers: { Accept: 'application/json' } })
  if (!respuesta.ok) throw new Error(`Nominatim ${respuesta.status}`)
  return respuesta.json()
}

/**
 * Busca lugares por nombre.
 *
 * Devuelve vacío si no hay ninguno o si el servicio no contesta: el buscador es
 * un atajo, y quien no lo consigue puede mover el mapa con el dedo.
 */
export async function buscarLugares(consulta) {
  const q = consulta.trim()
  if (q.length < 3) return []
  try {
    const datos = await consultar('/search', {
      q,
      format: 'jsonv2',
      addressdetails: 1,
      limit: 6,
      countrycodes: PAIS,
      'accept-language': 'es',
    })
    return datos
      .map((crudo) => {
        const lat = Number(crudo.lat)
        const lon = Number(crudo.lon)
        const nombre = texto(crudo.display_name)
        return Number.isFinite(lat) && Number.isFinite(lon) && nombre
          ? { nombre, lat, lon }
          : null
      })
      .filter(Boolean)
  } catch {
    return []
  }
}

/**
 * Deduce la dirección de un punto.
 *
 * Todo puede venir vacío: en un camino sin nombre no hay calle que deducir. Eso
 * no invalida la dirección —el punto sigue siendo exacto—, así que nada de esto
 * se exige.
 */
export async function describirPunto({ lat, lon }) {
  try {
    const datos = await consultar('/reverse', {
      lat,
      lon,
      format: 'jsonv2',
      addressdetails: 1,
      'accept-language': 'es',
    })
    const partes = datos.address ?? {}
    return {
      departamento: texto(partes.state),
      // El nombre del pueblo aparece en una clave distinta según el tamaño del
      // lugar; mirando solo `city`, en el interior queda siempre vacío.
      ciudad: texto(partes.city ?? partes.town ?? partes.village ?? partes.municipality ?? partes.county),
      barrio: texto(partes.suburb ?? partes.neighbourhood),
      calle: texto(partes.road ?? partes.pedestrian),
      numeroCasa: texto(partes.house_number),
      codigoPostal: texto(partes.postcode),
    }
  } catch {
    return {
      departamento: null,
      ciudad: null,
      barrio: null,
      calle: null,
      numeroCasa: null,
      codigoPostal: null,
    }
  }
}

/** La dirección detectada, en una línea legible. */
export function lineaDe(detectada) {
  const partes = [
    [detectada.calle, detectada.numeroCasa].filter(Boolean).join(' '),
    detectada.barrio,
    detectada.ciudad,
    detectada.departamento,
  ].filter(Boolean)
  return partes.length ? partes.join(', ') : null
}
