package com.techstore.tenant;

/**
 * La empresa a la que pertenece la petición que se está atendiendo.
 *
 * <p>Vive en un {@link ThreadLocal} porque en Spring MVC cada petición se atiende
 * en un hilo: lo que se guarda acá al empezar está disponible en todas las capas
 * sin tener que arrastrar el dato por cada firma de método.
 *
 * <p>De ahí también su principal cuidado: <b>hay que limpiarlo al terminar</b>.
 * Los hilos se reutilizan entre peticiones, y un tenant que queda pegado haría
 * que la siguiente persona vea los datos de otra empresa. De eso se encarga
 * {@link TenantFilter} en su bloque {@code finally}.
 *
 * <p>Cualquier trabajo que salga del hilo de la petición —un {@code @Async}, una
 * tarea programada— no hereda este valor y tiene que fijarlo explícitamente.
 */
public final class TenantContext {

    /** La empresa original, anterior a que el sistema fuera multi-empresa. */
    public static final Long TENANT_POR_DEFECTO = 1L;

    private static final ThreadLocal<Long> ACTUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void fijar(Long tenantId) {
        ACTUAL.set(tenantId);
    }

    /** El tenant de la petición actual, o el por defecto si nadie lo fijó. */
    public static Long actual() {
        Long valor = ACTUAL.get();
        return valor != null ? valor : TENANT_POR_DEFECTO;
    }

    /** Si hay uno fijado de verdad, sin caer al por defecto. */
    public static boolean hayTenant() {
        return ACTUAL.get() != null;
    }

    public static void limpiar() {
        ACTUAL.remove();
    }

    /**
     * Corre algo en nombre de otra empresa y restaura el anterior al terminar.
     * Para tareas de sistema que recorren varias empresas (por ejemplo, un
     * reporte consolidado o una limpieza programada).
     */
    public static <T> T comoTenant(Long tenantId, java.util.function.Supplier<T> accion) {
        Long previo = ACTUAL.get();
        try {
            ACTUAL.set(tenantId);
            return accion.get();
        } finally {
            if (previo != null) {
                ACTUAL.set(previo);
            } else {
                ACTUAL.remove();
            }
        }
    }
}
