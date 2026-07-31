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

### Venta directa (el cliente vino a la tienda)

Cuando el cliente compra en el mostrador sin haber hecho un pedido por la web,
usá **Admin → Venta directa**:

1. Buscá el producto y presioná **Agregar** (una vez por unidad, o ajustá la
   cantidad en el carrito). Al lado del precio ves cuántas unidades hay.
2. Si el cliente quiere su factura por correo, escribí su dirección en
   **Correo del cliente**. Si lo dejás vacío, la factura solo se imprime.
3. Cargá el pago igual que en un cobro normal: efectivo, transferencia o los
   dos. Si el cliente necesita factura con RUC, tildá *Facturar con RUC*.
4. **Registrar cobro**. En un solo paso se descuenta el stock, se registra el
   ingreso en caja y se emite la factura, que podés imprimir ahí mismo.

No hay que confirmar nada después: la mercadería sale en el momento, así que la
venta nace ya pagada. Queda en Admin → Pedidos marcada como *Venta directa en
mostrador*, para distinguirla de las ventas web.

> El sistema no deja vender unidades que estén reservadas por pedidos web
> pendientes: la cantidad que muestra es la realmente disponible.

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
4. Cuando llega la mercadería: botón **Recibir** → el stock real sube solo.
5. Botón **Pagar**: podés pagarle **todo en efectivo, todo por transferencia o
   repartido entre los dos**. Cada parte se anota como un egreso separado en la
   caja, para que el arqueo distinga lo que salió del cajón de lo que salió del
   banco. *Recibir* y *Pagar* son independientes: podés hacer uno sin el otro.

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

### Avisar cuándo reponer

Al editar un producto hay un campo **«Avisar cuando queden»**: es la cantidad
a partir de la cual el producto aparece en la lista de reposición, tanto en
Inicio como con una etiqueta naranja en la tabla de Productos.

Ponele a cada producto el número que tenga sentido para ese producto: no es lo
mismo quedarse con 2 placas madre que con 2 cables HDMI. Los que dejes en **0**
usan el aviso general, que es 5.

Ojo con esto: la cuenta se hace sobre el stock **disponible**, no sobre el que
tenés en el depósito. Si tenés 10 en el estante pero 8 ya están comprometidos
en pedidos sin entregar, para el sistema quedan 2. Es a propósito: si contara
los 10, el aviso te llegaría cuando ya no tenés nada que vender.

## 8. Usuarios

Admin → Usuarios: crear cajeros o administradores, desactivar cuentas
(no pueden entrar más) y resetear contraseñas. Cada persona puede cambiar
su propia contraseña haciendo clic en su **avatar** (arriba a la derecha).

Tras **5 intentos fallidos** seguidos con el mismo email, ese email queda
bloqueado **15 minutos**. El bloqueo se guarda en la base, así que reiniciar
el sistema no lo levanta.

### Auditoría

Admin → Auditoría: **quién hizo qué y cuándo**, con el valor de antes y el de
después. Queda registrado lo que toca la plata o la identidad del negocio:

- alta, edición y baja de productos (incluido cada cambio de precio),
- alta de usuarios, activación/desactivación y reseteo de contraseñas
  (nunca la contraseña en sí, solo que alguien la cambió),
- cambios en los datos del emisor de la factura,
- apertura y cierre de caja, con la diferencia del arqueo,
- movimientos de caja cargados a mano,
- notas de crédito y pagos a proveedores.

Es **solo lectura**: no hay forma de editar ni borrar estos registros desde el
sistema. Un registro que se puede modificar no sirve para lo único que sirve
una auditoría.

## 9. Reportes

Admin → Reportes, con filtro por fechas (Hoy / 7 días / 30 días / Todo):

- **Total facturado** y cantidad de facturas
- **Ventas por día**
- **Pedidos por estado**
- **Productos más vendidos** y **mejores clientes**
- **Stock bajo** con umbral configurable

## 10. Datos fiscales de la factura

La factura sale con formato oficial DNIT (timbrado, condición de venta,
columnas de IVA, total en letras). Los datos del **emisor** —los tuyos, los que
salen impresos y viajan dentro del Documento Electrónico— se editan desde
**Admin → Empresa**, sin tocar ningún archivo:

- **Datos generales**: nombre o razón social, RUC, dirección, ciudad, teléfono
  y actividad económica.
- **Timbrado**: número, vigencia impresa y fecha de inicio.
- **Datos fiscales del DE**: tipo de contribuyente, establecimiento, punto de
  expedición y los códigos de actividad, departamento, distrito y ciudad que
  exige el SIFEN. Si no estás seguro de esos códigos, dejalos como están.

El **establecimiento** y el **punto de expedición** son los que forman el número
de tus facturas (`001-001-0000001`): si los cambiás, las facturas nuevas salen
con el prefijo nuevo. Las ya emitidas no se tocan.

> ⚠️ Los valores de fábrica son de demostración. Antes de facturar de verdad,
> reemplazalos por los datos del contribuyente. Ojo con el **dígito verificador
> del RUC**: el de demostración (`80012345-6`) es inventado.

Si dejás un campo vacío, vuelve al valor de fábrica que está en
`techstore-backend/src/main/resources/application.properties`.

### El logo

El isotipo de TechStore (el rayo) sale impreso arriba del nombre en la factura,
en la nota de crédito y en el KuDE. También es el ícono de la pestaña del
navegador y el que se ve en la barra superior de la tienda.

Si querés poner **tu propio logo**, reemplazá estos dos archivos por los tuyos:

| Archivo | Para qué |
|---|---|
| `techstore-frontend/public/isotipo.svg` | La web (barra superior, login, ícono de la pestaña) |
| `techstore-backend/src/main/resources/logo/isotipo.png` | Los PDF. Cuadrado, 512×512, fondo transparente |

> Va aparte en PNG porque el generador de PDF no dibuja SVG. Si cambiás el SVG,
> acordate de exportar también el PNG. Y si el archivo faltara, los documentos
> se emiten igual, solo que sin logo.

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
