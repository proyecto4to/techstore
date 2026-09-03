import { useEffect, useState } from 'react'

/**
 * El valor, pero esperando a que la persona termine de escribir.
 *
 * Sirve para buscar mientras se tipea sin disparar una consulta por tecla. El
 * retardo por omisión es corto a propósito: en el mostrador la búsqueda tiene
 * que sentirse inmediata, y 250 ms alcanzan para agrupar las pulsaciones de una
 * palabra sin que se note la espera.
 */
export function useDebouncedValue(value, delay = 250) {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(id)
  }, [value, delay])

  return debounced
}
