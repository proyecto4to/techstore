import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { productoService } from '../services/productoService'
import ProductCard from '../components/product/ProductCard'

function CatalogoPage() {
  const [busqueda, setBusqueda] = useState('')
  const [search, setSearch] = useState('')
  const [marcaId, setMarcaId] = useState('')
  const [page, setPage] = useState(0)

  const { data: marcas = [] } = useQuery({
    queryKey: ['marcas'],
    queryFn: productoService.marcas,
    staleTime: 5 * 60 * 1000,
  })

  const {
    data: productos,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['productos', { search, marcaId, page }],
    queryFn: () =>
      productoService.listar({ search, marcaId: marcaId || null, page }),
    placeholderData: keepPreviousData,
  })

  const handleBuscar = (e) => {
    e.preventDefault()
    setPage(0)
    setSearch(busqueda)
  }

  return (
    <div className="container mx-auto p-6">
      <h2 className="mb-6 text-center text-4xl font-bold text-navy">Catálogo</h2>

      <form onSubmit={handleBuscar} className="mb-8 flex flex-col gap-3 sm:flex-row sm:justify-center">
        <input
          type="search"
          placeholder="Buscar productos…"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          className="w-full rounded border border-gray-300 p-2 focus:border-gold focus:outline-none sm:w-80"
        />
        <select
          value={marcaId}
          onChange={(e) => {
            setPage(0)
            setMarcaId(e.target.value)
          }}
          className="rounded border border-gray-300 p-2 focus:border-gold focus:outline-none"
        >
          <option value="">Todas las marcas</option>
          {marcas.map((marca) => (
            <option key={marca.id} value={marca.id}>
              {marca.nombre}
            </option>
          ))}
        </select>
        <button
          type="submit"
          className="rounded bg-navy px-6 py-2 font-semibold text-gold hover:bg-navy-light"
        >
          Buscar
        </button>
      </form>

      {isLoading && <p className="text-center text-gray-500">Cargando catálogo…</p>}
      {isError && (
        <p className="text-center text-red-500">
          No se pudo cargar el catálogo. ¿Está corriendo la API en el puerto 8080?
        </p>
      )}

      {productos && (
        <>
          {productos.content.length === 0 ? (
            <p className="text-center text-gray-500">No se encontraron productos.</p>
          ) : (
            <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {productos.content.map((producto) => (
                <ProductCard key={producto.id} producto={producto} />
              ))}
            </div>
          )}

          {productos.totalPages > 1 && (
            <div className="mt-8 flex items-center justify-center gap-4">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="rounded border border-navy px-4 py-2 text-navy disabled:opacity-40"
              >
                ← Anterior
              </button>
              <span className="text-navy">
                Página {productos.page + 1} de {productos.totalPages}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(productos.totalPages - 1, p + 1))}
                disabled={page >= productos.totalPages - 1}
                className="rounded border border-navy px-4 py-2 text-navy disabled:opacity-40"
              >
                Siguiente →
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

export default CatalogoPage
