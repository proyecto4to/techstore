package com.techstore.reparto;

import java.math.BigDecimal;
import java.util.List;

/** Puerto reemplazable: un proveedor externo puede sustituir el cálculo local. */
public interface OptimizadorRuta {
    RutaOptimizadaResponse optimizar(List<AsignacionReparto> asignaciones,
                                     BigDecimal salidaLatitud,
                                     BigDecimal salidaLongitud);
}
