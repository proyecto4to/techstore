function StatCard({ etiqueta, valor, detalle, icono }) {
  return (
    <div className="card card-hover group relative overflow-hidden">
      <div className="absolute inset-x-0 top-0 h-[3px] bg-gradient-to-r from-primary-400 to-accent-400 opacity-0 transition-opacity group-hover:opacity-100" />
      <div className="flex items-start justify-between">
        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-neutral-muted">
            {etiqueta}
          </p>
          <h3 className="text-3xl font-bold text-neutral-text">{valor}</h3>
          {detalle && <p className="mt-2 text-sm text-neutral-secondary">{detalle}</p>}
        </div>
        {icono && <div className="text-3xl opacity-60">{icono}</div>}
      </div>
    </div>
  )
}

export default StatCard
