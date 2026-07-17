import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { productoService } from '../services/productoService'
import { formatPrecio } from '../utils/format'
import AdminTabs from '../components/admin/AdminTabs'

const FORM_VACIO = {
  id: null,
  nombre: '',
  descripcion: '',
  precio: '',
  iva: 10,
  stockReal: 0,
  marcaId: '',
  activo: true,
}

function AdminProductosPage() {
  const [page, setPage] = useState(0)
  const [form, setForm] = useState(FORM_VACIO)
  const [mensaje, setMensaje] = useState(null)
  const [error, setError] = useState(null)
  const queryClient = useQueryClient()

  const { data: marcas = [] } = useQuery({
    queryKey: ['marcas'],
    queryFn: productoService.marcas,
    staleTime: 5 * 60 * 1000,
  })

  const { data, isLoading } = useQuery({
    queryKey: ['admin-productos', page],
    queryFn: () => productoService.listar({ page, size: 10, incluirInactivos: true }),
    placeholderData: keepPreviousData,
  })

  const refrescar = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-productos'] })
    queryClient.invalidateQueries({ queryKey: ['productos'] })
  }

  const onError = (err) => {
    const errores = err.response?.data?.errores
    setError(
      errores
        ? Object.entries(errores).map(([campo, msj]) => `${campo}: ${msj}`).join(' · ')
        : err.response?.data?.detail ?? 'La operación falló.',
    )
  }

  const aRequest = (f) => ({
    nombre: f.nombre.trim(),
    descripcion: f.descripcion?.trim() || null,
    precio: Number(f.precio),
    iva: Number(f.iva),
    stockReal: Number(f.stockReal),
    marcaId: f.marcaId ? Number(f.marcaId) : null,
    modeloId: null,
    activo: f.activo,
  })

  const guardarMutation = useMutation({
    mutationFn: (f) =>
      f.id ? productoService.actualizar(f.id, aRequest(f)) : productoService.crear(aRequest(f)),
    onSuccess: (producto, f) => {
      setError(null)
      setMensaje(`Producto "${producto.nombre}" ${f.id ? 'actualizado' : 'creado'}.`)
      setForm(FORM_VACIO)
      refrescar()
    },
    onError,
  })

  const desactivarMutation = useMutation({
    mutationFn: (id) => productoService.eliminar(id),
    onSuccess: () => {
      setError(null)
      setMensaje('Producto desactivado (baja lógica).')
      refrescar()
    },
    onError,
  })

  const reactivarMutation = useMutation({
    mutationFn: (producto) =>
      productoService.actualizar(producto.id, {
        nombre: producto.nombre,
        descripcion: producto.descripcion,
        precio: producto.precio,
        iva: producto.iva,
        stockReal: producto.stockReal,
        marcaId: producto.marca?.id ?? null,
        modeloId: producto.modelo?.id ?? null,
        activo: true,
      }),
    onSuccess: (producto) => {
      setError(null)
      setMensaje(`Producto "${producto.nombre}" reactivado.`)
      refrescar()
    },
    onError,
  })

  const editar = (producto) =>
    setForm({
      id: producto.id,
      nombre: producto.nombre,
      descripcion: producto.descripcion ?? '',
      precio: producto.precio,
      iva: producto.iva,
      stockReal: producto.stockReal,
      marcaId: producto.marca?.id ?? '',
      activo: producto.activo,
    })

  const handleSubmit = (e) => {
    e.preventDefault()
    guardarMutation.mutate(form)
  }

  const campo = (props) => ({
    className:
      'w-full rounded border border-gray-300 p-2 focus:border-gold focus:outline-none',
    ...props,
  })

  return (
    <div className="container mx-auto p-6">
      <h2 className="mb-6 text-3xl font-bold text-navy">Panel de administración — Productos</h2>
      <AdminTabs />

      {mensaje && (
        <p className="mb-4 rounded bg-green-50 p-3 text-green-700" role="status">
          {mensaje}
        </p>
      )}
      {error && (
        <p className="mb-4 rounded bg-red-50 p-3 text-red-600" role="alert">
          {error}
        </p>
      )}

      <form
        onSubmit={handleSubmit}
        className="mb-8 grid grid-cols-1 gap-4 rounded-lg border-2 border-gold bg-white p-6 shadow-md md:grid-cols-3"
      >
        <h3 className="col-span-full text-lg font-bold text-navy">
          {form.id ? `Editando producto #${form.id}` : 'Nuevo producto'}
        </h3>

        <div className="md:col-span-2">
          <label className="mb-1 block text-sm font-medium text-navy" htmlFor="nombre">
            Nombre *
          </label>
          <input
            id="nombre"
            required
            maxLength={255}
            value={form.nombre}
            onChange={(e) => setForm({ ...form, nombre: e.target.value })}
            {...campo()}
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-navy" htmlFor="marca">
            Marca
          </label>
          <select
            id="marca"
            value={form.marcaId}
            onChange={(e) => setForm({ ...form, marcaId: e.target.value })}
            {...campo()}
          >
            <option value="">Sin marca</option>
            {marcas.map((marca) => (
              <option key={marca.id} value={marca.id}>
                {marca.nombre}
              </option>
            ))}
          </select>
        </div>

        <div className="md:col-span-3">
          <label className="mb-1 block text-sm font-medium text-navy" htmlFor="descripcion">
            Descripción
          </label>
          <input
            id="descripcion"
            value={form.descripcion}
            onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
            {...campo()}
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-navy" htmlFor="precio">
            Precio (₲) *
          </label>
          <input
            id="precio"
            type="number"
            required
            min="1"
            step="0.01"
            value={form.precio}
            onChange={(e) => setForm({ ...form, precio: e.target.value })}
            {...campo()}
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-navy" htmlFor="iva">
            IVA (%) *
          </label>
          <input
            id="iva"
            type="number"
            required
            min="0"
            max="100"
            value={form.iva}
            onChange={(e) => setForm({ ...form, iva: e.target.value })}
            {...campo()}
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-navy" htmlFor="stock">
            Stock real *
          </label>
          <input
            id="stock"
            type="number"
            required
            min="0"
            value={form.stockReal}
            onChange={(e) => setForm({ ...form, stockReal: e.target.value })}
            {...campo()}
          />
        </div>

        <div className="col-span-full flex items-center gap-4">
          <button
            type="submit"
            disabled={guardarMutation.isPending}
            className="rounded bg-navy px-6 py-2 font-semibold text-gold hover:bg-navy-light disabled:opacity-50"
          >
            {form.id ? 'Guardar cambios' : 'Crear producto'}
          </button>
          {form.id && (
            <button
              type="button"
              onClick={() => setForm(FORM_VACIO)}
              className="rounded border border-navy px-6 py-2 text-navy hover:bg-gray-50"
            >
              Cancelar edición
            </button>
          )}
        </div>
      </form>

      {isLoading && <p className="text-gray-500">Cargando productos…</p>}

      {data && (
        <div className="overflow-x-auto rounded-lg border-2 border-gold bg-white shadow-md">
          <table className="w-full text-left">
            <thead className="bg-navy text-gold">
              <tr>
                <th className="p-3">Producto</th>
                <th className="p-3">Marca</th>
                <th className="p-3">Precio</th>
                <th className="p-3">Stock</th>
                <th className="p-3">Comprometido</th>
                <th className="p-3">Estado</th>
                <th className="p-3">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((producto) => (
                <tr key={producto.id} className={`border-t ${producto.activo ? '' : 'opacity-60'}`}>
                  <td className="p-3 font-medium text-navy">{producto.nombre}</td>
                  <td className="p-3">{producto.marca?.nombre ?? '—'}</td>
                  <td className="p-3">{formatPrecio(producto.precio)}</td>
                  <td className="p-3">{producto.stockReal}</td>
                  <td className="p-3">{producto.stockComprometido}</td>
                  <td className="p-3">
                    <span
                      className={`rounded px-2 py-1 text-xs font-semibold ${
                        producto.activo ? 'bg-green-100 text-green-800' : 'bg-gray-200 text-gray-600'
                      }`}
                    >
                      {producto.activo ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td className="p-3">
                    <div className="flex gap-2">
                      <button
                        onClick={() => editar(producto)}
                        className="rounded border border-navy px-3 py-1 text-sm text-navy hover:bg-gray-50"
                      >
                        Editar
                      </button>
                      {producto.activo ? (
                        <button
                          onClick={() => desactivarMutation.mutate(producto.id)}
                          disabled={desactivarMutation.isPending}
                          className="rounded border border-red-400 px-3 py-1 text-sm text-red-500 hover:bg-red-50 disabled:opacity-50"
                        >
                          Desactivar
                        </button>
                      ) : (
                        <button
                          onClick={() => reactivarMutation.mutate(producto)}
                          disabled={reactivarMutation.isPending}
                          className="rounded bg-gold px-3 py-1 text-sm font-semibold text-navy hover:bg-gold-light disabled:opacity-50"
                        >
                          Reactivar
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="rounded border border-navy px-4 py-2 text-navy disabled:opacity-40"
          >
            ← Anterior
          </button>
          <span className="text-navy">
            Página {data.page + 1} de {data.totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
            disabled={page >= data.totalPages - 1}
            className="rounded border border-navy px-4 py-2 text-navy disabled:opacity-40"
          >
            Siguiente →
          </button>
        </div>
      )}
    </div>
  )
}

export default AdminProductosPage
