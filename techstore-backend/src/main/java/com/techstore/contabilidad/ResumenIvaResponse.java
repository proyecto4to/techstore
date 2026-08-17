package com.techstore.contabilidad;

import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * La liquidación del IVA del período: lo que se cobró en las ventas contra lo
 * que se pagó en las compras.
 *
 * <p>El débito y el crédito ya vienen con las notas de crédito restadas, que es
 * lo que efectivamente se declara.
 */
public record ResumenIvaResponse(
        LocalDate desde,
        LocalDate hasta,
        String moneda,
        LibroIvaResponse.Totales ventas,
        LibroIvaResponse.Totales compras,
        /** IVA de las ventas: lo que se le cobró a los clientes. */
        BigDecimal debitoFiscal,
        /** IVA de las compras: lo que se le pagó a los proveedores. */
        BigDecimal creditoFiscal,
        /** Débito menos crédito. Positivo se paga; negativo queda a favor. */
        BigDecimal saldo,
        String situacion,
        List<String> avisos) {

    static ResumenIvaResponse de(LocalDate desde, LocalDate hasta, Moneda moneda,
                                 LibroIvaResponse libroVentas, LibroIvaResponse libroCompras) {
        Dinero debito = Dinero.de(libroVentas.totales().iva(), moneda);
        Dinero credito = Dinero.de(libroCompras.totales().iva(), moneda);
        Dinero saldo = debito.menos(credito);

        String situacion;
        if (saldo.esPositivo()) {
            situacion = "A pagar";
        } else if (saldo.esNegativo()) {
            situacion = "Saldo a favor";
        } else {
            situacion = "Sin saldo";
        }

        List<String> avisos = new ArrayList<>(libroVentas.avisos());
        avisos.addAll(libroCompras.avisos());

        return new ResumenIvaResponse(
                desde, hasta, moneda.codigo(),
                libroVentas.totales(), libroCompras.totales(),
                debito.monto(), credito.monto(), saldo.monto(),
                situacion, List.copyOf(avisos));
    }
}
