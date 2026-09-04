package com.techstore.seguridad;

/**
 * Los módulos sobre los que se dan permisos.
 *
 * <p>Son las áreas del negocio tal como las nombra quien administra, no los
 * paquetes del código: el administrador piensa en «Caja» y «Compras», no en
 * {@code com.techstore.caja}. Cada uno agrupa los endpoints de esa área.
 */
public enum Modulo {
    PRODUCTOS("Productos"),
    PEDIDOS("Pedidos"),
    COMPRAS("Compras"),
    PROVEEDORES("Proveedores"),
    CAJA("Caja"),
    FACTURAS("Facturas"),
    USUARIOS("Usuarios"),
    EMPRESA("Empresa"),
    ENVIOS("Envíos"),
    REPARTOS("Repartos"),
    VEHICULOS("Vehículos"),
    AUDITORIA("Auditoría"),
    CONTABILIDAD("Contabilidad"),
    CONFIGURACION("Configuración"),
    CONVERSACIONES("Conversaciones"),
    REPORTES("Reportes"),
    SEGURIDAD("Seguridad");

    private final String etiqueta;

    Modulo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Cómo se muestra en la pantalla de permisos. */
    public String etiqueta() {
        return etiqueta;
    }
}
