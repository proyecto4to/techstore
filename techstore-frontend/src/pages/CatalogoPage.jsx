import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { Search } from 'lucide-react'
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
    <div className="container mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="mb-2 text-4xl font-bold text-neutral-text">
          Catálogo de <span className="gradient-text">Productos</span>
        </h1>
        <p className="text-neutral-secondary">Componentes de PC de alta calidad</p>
      </div>

      <form onSubmit={handleBuscar} className="mb-8 flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1 sm:max-w-md">
          <Search className="absolute left-4 top-2.5 text-neutral-muted" size={18} />
          <input
            type="search"
            placeholder="Buscar productos…"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="input pl-11"
          />
        </div>
        <select
          value={marcaId}
          onChange={(e) => {
            setPage(0)
            setMarcaId(e.target.value)
          }}
          className="input sm:w-56"
        >
          <option value="">Todas las marcas</option>
          {marcas.map((marca) => (
            <option key={marca.id} value={marca.id}>
              {marca.nombre}
            </option>
          ))}
        </select>
        <button type="submit" className="btn btn-primary">
          Buscar
        </button>
      </form>

      {isLoading && <p className="text-center text-neutral-muted">Cargando catálogo…</p>}
      {isError && (
        <p className="alerta-error text-center">
          No se pudo cargar el catálogo. ¿Está corriendo la API en el puerto 8090?
        </p>
      )}

      {productos && (
        <>
          {productos.content.length === 0 ? (
            <p className="text-center text-neutral-muted">No se encontraron productos.</p>
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
                className="btn btn-secondary"
              >
                ← Anterior
              </button>
              <span className="text-neutral-secondary">
                Página {productos.page + 1} de {productos.totalPages}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(productos.totalPages - 1, p + 1))}
                disabled={page >= productos.totalPages - 1}
                className="btn btn-secondary"
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
