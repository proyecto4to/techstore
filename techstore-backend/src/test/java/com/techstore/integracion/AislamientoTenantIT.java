package com.techstore.integracion;

import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El test más importante de la tanda: que una empresa no pueda ver los datos de
 * otra.
 *
 * <p>Es el peor modo de fallar que tiene un SaaS y el más fácil de introducir
 * sin darse cuenta —alcanza con un {@code WHERE} olvidado—, así que conviene
 * que esté verificado y no supuesto.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Aislamiento entre empresas")
class AislamientoTenantIT {

    private static final long OTRA_EMPRESA = 2L;

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        BaseDatosDePrueba.registrar(registro);
    }

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
    }

    private void crearOtraEmpresa() throws SQLException {
        try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
            st.execute("INSERT INTO tenants (id, codigo, nombre) VALUES (2, 'otra', 'Otra Empresa') "
                    + "ON CONFLICT (id) DO NOTHING");
        }
    }

    private Producto nuevoProducto(String nombre) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setPrecio(com.techstore.comun.Dinero.de(1_000, com.techstore.comun.Moneda.PYG));
        p.setStockReal(5);
        p.setStockComprometido(0);
        p.setStockMinimo(0);
        p.setActivo(true);
        return p;
    }

    @Test
    @DisplayName("cada empresa solo ve sus propios productos")
    void unaEmpresaNoVeLosProductosDeLaOtra() throws SQLException {
        crearOtraEmpresa();

        TenantContext.fijar(TenantContext.TENANT_POR_DEFECTO);
        Producto mio = productoRepository.save(nuevoProducto("Solo de la empresa 1"));
        long idMio = mio.getId();

        TenantContext.fijar(OTRA_EMPRESA);
        Producto ajeno = productoRepository.save(nuevoProducto("Solo de la empresa 2"));
        long idAjeno = ajeno.getId();

        // Desde la empresa 2 no existe el producto de la 1.
        assertTrue(productoRepository.findById(idAjeno).isPresent(), "No ve ni lo suyo");
        assertEquals(Optional.empty(), productoRepository.findById(idMio),
                "La empresa 2 puede leer un producto de la empresa 1 por id");

        // Y al revés.
        TenantContext.fijar(TenantContext.TENANT_POR_DEFECTO);
        assertTrue(productoRepository.findById(idMio).isPresent());
        assertEquals(Optional.empty(), productoRepository.findById(idAjeno),
                "La empresa 1 puede leer un producto de la empresa 2 por id");
    }

    @Test
    @DisplayName("los listados tampoco filtran de más ni de menos")
    void losListadosVienenFiltrados() throws SQLException {
        crearOtraEmpresa();

        TenantContext.fijar(OTRA_EMPRESA);
        productoRepository.save(nuevoProducto("Listado empresa 2 - A"));
        productoRepository.save(nuevoProducto("Listado empresa 2 - B"));

        // La comprobación se hace contra la base y no contra una lista de nombres
        // esperados: los tests de esta clase comparten la base, así que enumerar
        // "lo que debería estar" es frágil. Lo que importa es que no aparezca
        // NADA de la otra empresa.
        List<Long> idsVistosPor2 = productoRepository.findAll().stream().map(Producto::getId).toList();
        assertEquals(0, cuantosDeEsosSonDelTenant(idsVistosPor2, 1L),
                "Se colaron productos de la empresa 1 en el listado de la empresa 2");
        assertFalse(idsVistosPor2.isEmpty(), "La empresa 2 no ve ni sus propios productos");

        TenantContext.fijar(TenantContext.TENANT_POR_DEFECTO);
        List<Long> idsVistosPor1 = productoRepository.findAll().stream().map(Producto::getId).toList();
        assertEquals(0, cuantosDeEsosSonDelTenant(idsVistosPor1, OTRA_EMPRESA),
                "Se colaron productos de la empresa 2 en el listado de la empresa 1");
        // El catálogo semilla pertenece a la empresa 1: tiene que seguir estando.
        assertTrue(idsVistosPor1.size() > 2, "Desapareció el catálogo de la empresa original");
    }

    @Test
    @DisplayName("al guardar, el tenant se completa solo")
    void elTenantSeCompletaAlInsertar() throws SQLException {
        crearOtraEmpresa();
        TenantContext.fijar(OTRA_EMPRESA);

        // Nadie asigna tenantId a mano: lo pone Hibernate.
        Producto p = productoRepository.save(nuevoProducto("Tenant automático"));

        assertEquals(OTRA_EMPRESA, p.getTenantId(),
                "Hibernate no completó el tenant al insertar");
        assertEquals(1, contar("SELECT count(*) FROM productos WHERE id = " + p.getId()
                + " AND tenant_id = " + OTRA_EMPRESA));
    }

    @Test
    @DisplayName("cada empresa numera su propia serie de facturas")
    void laNumeracionEsPorEmpresa() throws SQLException {
        crearOtraEmpresa();
        // Las dos empresas pueden tener a la vez su factura 001-001-0000001:
        // la restricción única ahora es (tenant_id, numero), no solo numero.
        try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
            st.execute("INSERT INTO autonumerador (tenant_id, entidad, valor) VALUES (1, 'PRUEBA_SERIE', 10) "
                    + "ON CONFLICT DO NOTHING");
            st.execute("INSERT INTO autonumerador (tenant_id, entidad, valor) VALUES (2, 'PRUEBA_SERIE', 99) "
                    + "ON CONFLICT DO NOTHING");
        }
        assertEquals(2, contar("SELECT count(*) FROM autonumerador WHERE entidad = 'PRUEBA_SERIE'"),
                "El autonumerador no admite la misma entidad en dos empresas: las series se pisarían");
    }

    /** Cuántos de esos ids pertenecen, según la base, a la empresa indicada. */
    private long cuantosDeEsosSonDelTenant(List<Long> ids, long tenantId) throws SQLException {
        if (ids.isEmpty()) {
            return 0;
        }
        String lista = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow();
        return contar("SELECT count(*) FROM productos WHERE tenant_id = " + tenantId
                + " AND id IN (" + lista + ")");
    }

    private long contar(String sql) throws SQLException {
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
