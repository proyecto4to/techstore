# 📖 Manual de usuario — TechStore

Guía práctica para operar el sistema, escrita para el día a día del negocio
(no hace falta saber de programación).

---

## 1. Encender y apagar el sistema

| Acción | Cómo |
|---|---|
| **Encender** | Doble clic en el ícono **TechStore** del escritorio. Se abre solo el navegador con la tienda cuando todo está listo (30–60 segundos la primera vez). |
| **Apagar** | Doble clic en **TechStore - Detener**. También podés simplemente apagar la PC: no se pierde nada, y el próximo inicio se recupera solo. |

Direcciones útiles: la tienda vive en `http://localhost:5173` y la
documentación técnica de la API en `http://localhost:8080/swagger-ui.html`.

## 2. Roles y qué puede hacer cada uno

| Rol | Puede |
|---|---|
| **Cliente** | Navegar el catálogo, comprar, ver y anular sus pedidos pendientes, descargar su factura, cambiar su contraseña. |
| **Cajero** | Todo lo operativo: confirmar y cobrar pedidos, emitir facturas y notas de crédito, manejar la caja, registrar compras a proveedores y ver reportes. |
| **Admin** | Todo lo anterior + gestionar productos, usuarios y proveedores. |

Usuarios de demostración:

- Admin: `admin@techstore.py` / `admin123`
- Cajero: `cajero@techstore.py` / `cajero1234`

## 3. El ciclo de una venta

1. **El cliente compra**: elige productos → carrito → *Finalizar compra*.
   El pedido queda **Pendiente** y el stock se reserva (nadie más puede
   comprar esas unidades).
2. **Confirmar** (Admin → Pedidos): la mercadería se descuenta del stock real.
3. **Cobrar**: elegí el medio de pago y presioná *Cobrar*. En un solo paso se
   registra el pago, se **emite la factura** (numeración fiscal, IVA
   desglosado) y se anota el **ingreso en caja**. El pedido queda **Pagado**.
4. El cliente ve el botón **Factura** en *Mis pedidos* y puede imprimirla.

¿El cliente se arrepintió antes de confirmar? *Anular* libera el stock.

## 4. Devoluciones (notas de crédito)

Solo sobre pedidos **Pagados**. En Admin → Pedidos, botón **Devolver**:
escribí el motivo y confirmá. En una sola operación el sistema:

- emite la **nota de crédito** con su numeración fiscal (PDF imprimible en la pestaña Facturas),
- **anula la factura** original,
- **devuelve la mercadería al stock**, y
- registra el **egreso en caja** (la plata que devolvés al cliente).

## 5. Compras a proveedores (reponer stock)

En Admin → Compras:

1. Elegí el proveedor (o creálo ahí mismo con *+ Nuevo proveedor*).
2. Agregá los productos con la **cantidad** y el **costo unitario** que te cobra el proveedor.
3. *Registrar compra* — queda **Pendiente**, el stock todavía no cambia.
4. Cuando llega la mercadería: botón **Recibir** → el stock real sube solo.

## 6. Caja

En Admin → Caja ves el arqueo (**ingresos, egresos y saldo**) filtrable por
fechas. Los cobros y devoluciones se registran solos; para gastos varios
(servicios, insumos) usá *Registrar movimiento manual*.

## 7. Productos

Admin → Productos: crear, editar precio/stock/IVA, poner imagen (una ruta
local o cualquier URL de internet), y **desactivar** lo que ya no vendés
(desaparece de la tienda sin borrar su historial; se puede reactivar).

La pestaña **Reportes** avisa cuando un producto tiene **stock bajo**.

## 8. Usuarios

Admin → Usuarios: crear cajeros o administradores, desactivar cuentas
(no pueden entrar más) y resetear contraseñas. Cada persona puede cambiar
su propia contraseña haciendo clic en su **avatar** (arriba a la derecha).

## 9. Reportes

Admin → Reportes, con filtro por fechas (Hoy / 7 días / 30 días / Todo):

- **Total facturado** y cantidad de facturas
- **Ventas por día**
- **Pedidos por estado**
- **Productos más vendidos** y **mejores clientes**
- **Stock bajo** con umbral configurable

## 10. Datos fiscales de la factura

La factura sale con formato oficial DNIT (timbrado, condición de venta,
columnas de IVA, total en letras). Los datos de la empresa se configuran en
`techstore-backend/src/main/resources/application.properties`:

```properties
empresa.nombre=TechStore Paraguay
empresa.ruc=80012345-6            ← poné tu RUC real
empresa.timbrado=12345678         ← tu timbrado real
empresa.timbrado-vigencia=01/01/2026 al 31/12/2026
empresa.direccion=...             (usar ñ para ñ, á para á, ó para ó)
```

> ⚠️ Los valores de fábrica son de demostración. Antes de facturar de
> verdad, reemplazalos por los datos del contribuyente.

## 11. Preguntas frecuentes

**Se apagó la PC de golpe, ¿perdí datos?**
No. La base de datos se recupera sola en el próximo inicio.

**Me olvidé la contraseña de admin.**
Otro Admin puede resetearla desde Usuarios. Si no hay otro, contactá soporte técnico.

**¿Puedo usarlo desde otra computadora?**
El sistema corre local en esta PC. Publicarlo en red o internet es una
configuración aparte (consultá soporte técnico).

**¿Un cliente puede ver los datos de otro?**
No: cada cliente ve solo sus pedidos y facturas; las pantallas de
administración exigen rol Admin o Cajero.
