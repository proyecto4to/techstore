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

### Facturar con RUC

Por defecto la factura sale a **consumidor final**. Si el cliente necesita su
RUC, al cobrar tildá **🧾 Facturar con RUC** y completá:

- **RUC** (obligatorio, formato `80012345-0`). El sistema **verifica el dígito
  verificador**: si está mal, no deja emitir. Es a propósito — un RUC mal
  tipeado genera una factura electrónica inválida.
- **Razón social** (opcional): si la dejás vacía se usa el nombre del cliente.
- **Guardar en la cuenta del cliente**: deja el RUC cargado para que la próxima
  compra ya salga a su nombre sin tener que dictarlo.

El cliente también puede cargar su RUC por su cuenta, en **Mi cuenta → Datos
para la factura**. Si lo tiene cargado, el cobro lo trae ya completado.

> ⚠️ **Una factura emitida no se edita.** Si salió a consumidor final y el
> cliente después pide su RUC, el camino correcto es hacer la **devolución**
> (nota de crédito, sección 4) y volver a facturar con el RUC.

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
4. Cuando llega la mercadería: botón **Recibir** → el stock real sube solo y, en el
   mismo paso, el pago al proveedor se anota como **egreso en la caja**.

## 6. Caja

En Admin → Caja ves el arqueo del período, filtrable por fechas y exportable a
PDF. Los cobros y devoluciones se registran solos; para gastos varios
(servicios, insumos) usá *Registrar movimiento manual*.

El total va **separado por medio**, porque no es lo mismo:

| Tarjeta | Qué es |
|---|---|
| **Efectivo (cajón)** | La plata que entró y salió del cajón. Es la que contás al cerrar. |
| **Transferencias (banco)** | Lo que se movió por transferencia: no pasa por el cajón. |
| **Total del período** | Los dos juntos. Sirve para ver el movimiento general, **no** cuánta plata hay en la caja. |

> Antes había una sola tarjeta "Saldo de caja" que sumaba los dos medios, así
> que podía mostrar un número positivo aunque el efectivo estuviera en rojo.

### Abrir y cerrar la caja del turno

1. **Al empezar el turno**: contá la plata que hay en el cajón y presioná
   **Abrir caja** con ese monto. Puede ser el fondo fijo, o lo que quedó del
   turno anterior — lo que realmente esté ahí.
2. Durante el turno, cobrás normalmente: los ingresos se registran solos.
3. **Al terminar**: contá el efectivo y presioná **Registrar cierre**. El
   sistema compara tu conteo contra el **efectivo teórico**:

   > efectivo teórico = saldo inicial + ingresos en efectivo − egresos en efectivo

   y te muestra el **sobrante o faltante**.

Las **transferencias se informan aparte**: van al banco, no al cajón, así que
no entran en el efectivo teórico. Se muestran los ingresos, los egresos (por
ejemplo pagos a proveedor) y el **neto bancario** del turno.

Si mientras contabas la plata entró un cobro nuevo, al presionar *Registrar
cierre* el sistema te avisa que el teórico cambió en vez de marcarte una
diferencia que no existe: revisás el conteo y volvés a confirmar.

> Ningún movimiento se pierde del arqueo: cada cierre toma **todos los
> movimientos que todavía no fueron arqueados**, sin importar su fecha. Si un
> cobro se registra con la caja cerrada, lo toma el cierre siguiente.

> ⚠️ Si no abrís la caja, el sistema no te deja cerrarla — sin saber con
> cuánto arrancaste, el arqueo no significa nada. Y si al cerrar dejás la
> plata en el cajón, acordate de declarar ese mismo monto como saldo inicial
> del próximo turno.

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

## 11. Factura electrónica (SIFEN) — demostrativo

En la pestaña **Facturas**, cada factura tiene además de su PDF dos botones:

- **KuDE**: la representación gráfica del Documento Electrónico con el **CDC**
  (Código de Control de 44 dígitos) y el **código QR** de consulta del SIFEN.
- **XML**: descarga el Documento Electrónico **firmado digitalmente**, en el
  formato del Manual Técnico del SIFEN v150.

> ⚠️ **Es una demostración del estándar.** El documento se firma con un
> certificado de **prueba** y **no se transmite a la SET/DNIT**. Para facturar
> electrónicamente de verdad hacen falta dos trámites externos: un **certificado
> digital** emitido por un prestador habilitado y la **habilitación como
> facturador electrónico** ante la DNIT (con timbrado electrónico). Recién con
> eso se puede firmar con validez legal y enviar a los web services del SIFEN.

Los datos fiscales del emisor (RUC, timbrado, establecimiento, punto de
expedición, actividad económica, ubicación) se configuran en
`application.properties` bajo `empresa.*`.

## 12. Notificaciones por correo (bandeja de salida)

El sistema **registra un correo** en cada evento importante: emisión de una
**factura** (con el PDF adjunto), **alta** de un cliente, **cambio** de la
propia contraseña, **reseteo** de contraseña por un admin y **desactivación**
de una cuenta. Todos se ven en **Admin → Correos**.

Por defecto los correos quedan en estado **Simulado**: se registran localmente
pero **no se envían** de verdad (sin costo ni configuración). Para enviar por
correo real:

1. Copiá el archivo `email.local.ps1.ejemplo` como **`email.local.ps1`** (ese
   queda fuera de git, no se sube).
2. Poné tu casilla de Gmail y una **Contraseña de aplicación** de Google (con la
   Verificación en 2 pasos activada: https://myaccount.google.com/apppasswords).
3. Volvé a iniciar TechStore. Los correos pasan a **Enviado** (o **Error** si el
   SMTP falla, con el detalle a la vista en Admin → Correos).

> Un usuario **desactivado** que intente entrar verá el mensaje *"Tu cuenta
> está bloqueada o inactiva. Contactá al administrador"* en la pantalla de acceso.

## 13. Respaldos de la base (copias de seguridad)

Tus datos se respaldan **solos**: cada vez que iniciás o detenés el sistema
con los íconos del escritorio se guarda una copia de la base. Se conservan
los últimos 20 respaldos en `%LOCALAPPDATA%\TechStore\backups` (fuera de
OneDrive, a propósito).

- **Respaldar ahora**: ícono **TechStore - Respaldar** (útil antes de una
  carga grande de datos o cualquier cambio importante).
- **Restaurar**: ícono **TechStore - Restaurar** → te muestra la lista de
  copias con su fecha, elegís una y confirmás escribiendo `SI`. Reemplaza el
  contenido actual por el de esa copia. Después volvé a iniciar TechStore.

> Si borraste algo por error o la base se dañó, restaurá el respaldo más
> reciente y perdés, como mucho, lo hecho desde ese momento.

## 14. Preguntas frecuentes

**Se apagó la PC de golpe, ¿perdí datos?**
No. La base de datos se recupera sola en el próximo inicio, y además hay
respaldos automáticos (ver sección 13) por si alguna vez hiciera falta.

**Me olvidé la contraseña de admin.**
Otro Admin puede resetearla desde Usuarios. Si no hay otro, contactá soporte técnico.

**¿Puedo usarlo desde otra computadora?**
El sistema corre local en esta PC. Publicarlo en red o internet es una
configuración aparte (consultá soporte técnico).

**¿Un cliente puede ver los datos de otro?**
No: cada cliente ve solo sus pedidos y facturas; las pantallas de
administración exigen rol Admin o Cajero.
