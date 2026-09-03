import { AlertCircle, RotateCw } from 'lucide-react'

/**
 * Lo que se muestra cuando una lista no se pudo traer.
 *
 * Sin esto, al fallar la consulta la pantalla quedaba en blanco: ni lista, ni
 * mensaje. No había forma de distinguir «no hay nada cargado» de «algo se
 * rompió», y no quedaba nada por hacer más que recargar la página entera.
 */
export default function ErrorDeCarga({ que = 'la información', onReintentar }) {
  return (
    <div className="card flex flex-col items-center gap-3 py-10 text-center" role="alert">
      <AlertCircle className="text-red-400" size={32} aria-hidden="true" />
      <p className="text-neutral-text">No pudimos cargar {que}.</p>
      <p className="text-sm text-neutral-secondary">
        Puede ser un problema de conexión. Probá de nuevo en un momento.
      </p>
      {onReintentar && (
        <button
          type="button"
          onClick={onReintentar}
          className="btn btn-secondary mt-1 inline-flex items-center gap-2"
        >
          <RotateCw size={16} aria-hidden="true" /> Reintentar
        </button>
      )}
    </div>
  )
}
