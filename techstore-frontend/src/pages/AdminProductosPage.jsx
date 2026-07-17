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

  return (
    <div className="container mx-auto px-6 py-8">
      <h1 className="mb-6 text-4xl font-bold text-neutral-text">
        ⚙️ Administración — <span className="gradient-text">Productos</span>
      </h1>
      <AdminTabs />

      {mensaje && (
        <p className="alerta-ok" role="status">
          {mensaje}
        </p>
      )}
      {error && (
        <p className="alerta-error" role="alert">
          {error}
        </p>
      )}

      <form onSubmit={handleSubmit} className="card mb-8 grid grid-cols-1 gap-4 md:grid-cols-3">
        <h3 className="col-span-full text-lg font-semibold text-neutral-text">
          {form.id ? `Editando producto #${form.id}` : '➕ Nuevo producto'}
        </h3>

        <div className="md:col-span-2">
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="nombre">
            Nombre *
          </label>
          <input
            id="nombre"
            required
            maxLength={255}
            value={form.nombre}
            onChange={(e) => setForm({ ...form, nombre: e.target.value })}
            className="input"
          />
        </div>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="marca">
            Marca
          </label>
          <select
            id="marca"
            value={form.marcaId}
            onChange={(e) => setForm({ ...form, marcaId: e.target.value })}
            className="input"
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
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="descripcion">
            Descripción
          </label>
          <input
            id="descripcion"
            value={form.descripcion}
            onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
            className="input"
          />
        </div>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="precio">
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
            className="input"
          />
        </div>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="iva">
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
            className="input"
          />
        </div>

        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-text" htmlFor="stock">
            Stock real *
          </label>
          <input
            id="stock"
            type="number"
            required
            min="0"
            value={form.stockReal}
            onChange={(e) => setForm({ ...form, stockReal: e.target.value })}
            className="input"
          />
        </div>

        <div className="col-span-full flex items-center gap-4">
          <button type="submit" disabled={guardarMutation.isPending} className="btn btn-primary">
            {form.id ? 'Guardar cambios' : 'Crear producto'}
          </button>
          {form.id && (
            <button type="button" onClick={() => setForm(FORM_VACIO)} className="btn btn-secondary">
              Cancelar edición
            </button>
          )}
        </div>
      </form>

      {isLoading && <p className="text-neutral-muted">Cargando productos…</p>}

      {data && (
        <div className="card overflow-x-auto p-0">
          <table className="tabla-admin">
            <thead>
              <tr>
                <th>Producto</th>
                <th>Marca</th>
                <th>Precio</th>
                <th>Stock</th>
                <th>Comprometido</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((producto) => (
                <tr key={producto.id} className={producto.activo ? '' : 'opacity-50'}>
                  <td className="font-medium text-neutral-text">{producto.nombre}</td>
                  <td className="text-neutral-secondary">{producto.marca?.nombre ?? '—'}</td>
                  <td className="text-primary-300">{formatPrecio(producto.precio)}</td>
                  <td>{producto.stockReal}</td>
                  <td>{producto.stockComprometido}</td>
                  <td>
                    <span
                      className={`rounded-full px-3 py-1 text-xs font-semibold ${
                        producto.activo
                          ? 'bg-emerald-400/20 text-emerald-400'
                          : 'bg-neutral-muted/20 text-neutral-muted'
                      }`}
                    >
                      {producto.activo ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td>
                    <div className="flex gap-2">
                      <button onClick={() => editar(producto)} className="btn btn-secondary text-sm">
                        Editar
                      </button>
                      {producto.activo ? (
                        <button
                          onClick={() => desactivarMutation.mutate(producto.id)}
                          disabled={desactivarMutation.isPending}
                          className="btn btn-danger text-sm"
                        >
                          Desactivar
                        </button>
                      ) : (
                        <button
                          onClick={() => reactivarMutation.mutate(producto)}
                          disabled={reactivarMutation.isPending}
                          className="btn btn-primary text-sm"
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
            className="btn btn-secondary"
          >
            ← Anterior
          </button>
          <span className="text-neutral-secondary">
            Página {data.page + 1} de {data.totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
            disabled={page >= data.totalPages - 1}
            className="btn btn-secondary"
          >
            Siguiente →
          </button>
        </div>
      )}
    </div>
  )
}

export default AdminProductosPage
