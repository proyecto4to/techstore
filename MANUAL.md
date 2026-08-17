# 📖 Manual de usuario — TechStore

Guía práctica para operar el sistema, escrita para el día a día del negocio
(no hace falta saber de programación).

---

## 1. Encender y apagar el sistema

| Acción | Cómo |
|---|---|
| **Encender** | Doble clic en el ícono **TechStore** del escritorio. Se abre solo el navegador con la tienda cuando todo está listo (30–60 segundos la primera vez). |
| **Apagar** | Doble clic en **TechStore - Detener**. También podés simplemente apagar la PC: no se pierde nada, y el próximo inicio se recupera solo. |

Este es el modo **local predeterminado** y conserva los accesos directos
actuales. El apagado solo detiene los procesos que el propio lanzador registró;
si 5173, 8090 o 5432 pertenecen a otro programa, TechStore muestra el conflicto
y no lo cierra.

Direcciones útiles: la tienda vive en `http://localhost:5173` y la
documentación técnica de la API en `http://localhost:8090/swagger-ui.html`.

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
4. Botón **Enviar**: le manda el pedido al proveedor con el detalle de productos,
   cantidades y total. Elegís el canal — hoy anda el **correo**; WhatsApp aparece
   pero está deshabilitado, porque mandar en nombre de un negocio exige una
   cuenta de *WhatsApp Business API* (Meta Cloud API o Twilio), con verificación
   del negocio y costo por conversación.
   - El proveedor **puede responderte**: la respuesta llega a la casilla que
     tengas cargada en Admin → Empresa.
   - Si el proveedor no tiene correo cargado, el sistema no te deja mandarlo y te
     dice que lo completes.
   - **Mandar el pedido no lo confirma.** La compra sigue Pendiente hasta que
     llegue la mercadería. Si el correo no llega a salir (por ejemplo, si no hay
     servidor de correo configurado), la compra **no** queda marcada como
     enviada: te avisa qué pasó en vez de dejarte creyendo que el proveedor lo
     recibió.
5. Cuando llega la mercadería: botón **Recibir** → el stock real sube solo.
6. Botón **Pagar**: podés pagarle **todo en efectivo, todo por transferencia o
   repartido entre los dos**. Cada parte se anota como un egreso separado en la
   caja, para que el arqueo distinga lo que salió del cajón de lo que salió del
   banco. *Recibir* y *Pagar* son independientes: podés hacer uno sin el otro.
7. Botón **Comprobante**: cargá la factura que mandó el proveedor (número,
   timbrado y fecha). **Esto no es opcional si vas a presentar libros**: lo que
   se declara ante la DNIT es el documento del proveedor, no el pedido que
   cargaste vos. Si dejás los importes vacíos, se toman los de la compra; si el
   papel dice otra cosa (un flete, una diferencia de precio), cargalos a mano y
   manda el papel.

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

## 10. Contabilidad: libros de IVA y presentación a la DNIT

Admin → Contabilidad (solo Admin). Tiene cuatro solapas.

### Liquidación del IVA

Lo que cobraste de IVA en las ventas (**débito fiscal**) contra lo que pagaste
en las compras (**crédito fiscal**). La diferencia es lo que se paga, o lo que
queda a favor para el período siguiente.

### Libro de ventas y libro de compras

Los comprobantes del período en orden cronológico, con el desglose gravadas al
10% / al 5% / exentas y el IVA de cada uno. Se exportan a PDF.

- Las facturas **anuladas no aparecen**: un documento anulado no se declara, y
  el hueco en la numeración es correcto.
- Las **notas de crédito** figuran en rojo y con signo negativo: restan.
- El libro de compras va por la **fecha del comprobante del proveedor**, no por
  la fecha en que cargaste la compra.
- Con **Desde = Hasta** el libro de ventas te sirve de **cierre diario**.

### Archivo para la DNIT

Genera el ZIP del **Registro de Comprobantes** que se sube a *Marangatu*
(RG 90/2021, el que reemplazó a Hechauka). Elegís año y mes —o marcás *Registro
anual*— y te lo descarga con el nombre reglamentario. **Subilo tal cual, sin
descomprimirlo.**

- El **vencimiento** sale del calendario perpetuo: la última cifra de tu RUC sin
  dígito verificador decide el día del mes siguiente (0 → 7, 1 → 9, 2 → 11, y
  así hasta 9 → 25).
- Si al período le falta algún dato obligatorio **el archivo no se genera**, y
  la pantalla te lista qué falta. No es una molestia gratuita: Marangatu rechaza
  el archivo entero, no la fila con el problema.
- El formato **TXT** viene por defecto porque separa con tabulaciones y no hay
  forma de que se confunda con el texto de una razón social.
- **Impuesto a la renta**: elegís si los comprobantes se imputan al IRE o al
  IRP-RSP. Depende de las obligaciones que tengas inscriptas en el RUC, así que
  el sistema no lo adivina.

> **Ojo con lo que la DNIT ya tiene.** El archivo no debe incluir comprobantes
> emitidos por e-Kuatia. Hoy TechStore arma el KuDE pero **no transmite al
> SIFEN**, así que sus facturas sí van en el archivo. Si algún día se transmite
> de verdad, esto hay que revisarlo o cada venta quedaría declarada dos veces.

### Qué libros exige la ley, para ubicarse

- **Libro Diario e Inventario**: obligatorios por la Ley 1034/83 "Del
  Comerciante", rubricados por el **Registro Público de Comercio**, no por la
  DNIT. Requieren plan de cuentas y partida doble: **el sistema todavía no los
  lleva**.
- **Libros de Ventas y Compras (IVA)** y **Libro Mayor**: los pide la RG
  412/2004. La resolución no fija columnas, solo exige discriminar gravadas,
  exoneradas y exportación con el IVA por separado — que es lo que imprimen los
  libros de acá.
- **Registro de Comprobantes**: es lo que hoy se presenta efectivamente, y es lo
  que genera la solapa anterior.

## 11. Datos fiscales de la factura

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

## 12. Factura electrónica (SIFEN) — demostrativo

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

## 13. Notificaciones por correo (bandeja de salida)

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

## 14. Respaldos y restauración

Tus datos se respaldan **solos**: cada vez que iniciás o detenés el modo local
con los íconos del escritorio se crea un conjunto que contiene la **base**, las
**fotos/archivos subidos** y un manifiesto con hashes SHA-256. El dump y el ZIP
se validan antes de publicarse; si el respaldo de cierre falla, PostgreSQL queda
encendido y se muestra el error. Se conservan los últimos 20 conjuntos en
`%LOCALAPPDATA%\TechStore\backups` (fuera de OneDrive, a propósito).

- **Respaldar ahora**: ícono **TechStore - Respaldar** (útil antes de una
  carga grande de datos o cualquier cambio importante).
- **Restaurar**: ícono **TechStore - Restaurar** → te muestra la lista, valida
  dump, manifiesto, hashes y ZIP, y pide confirmación escribiendo `RESTAURAR`.
  Para una copia antigua sin manifiesto exige `RESTAURAR-SOLO-BD`. Antes de
  reemplazar nada crea otro respaldo de seguridad. La base se restaura en una
  transacción y los uploads se intercambian con rollback razonable si algo
  falla. Después volvé a iniciar TechStore con el acceso directo habitual.

Los respaldos antiguos que no tienen manifiesto siguen siendo compatibles,
pero restauran únicamente la base y dejan los uploads actuales sin cambios.
Estos íconos administran la instalación **local/portable**; una instalación
Docker comercial debe respaldar externamente ambos volúmenes (`postgres_data`
y `uploads_data`) y ensayar su procedimiento de restauración.

> Si borraste algo por error o la base se dañó, restaurá el respaldo más
> reciente y perdés, como mucho, lo hecho desde ese momento.

## 15. Preguntas frecuentes

**Se apagó la PC de golpe, ¿perdí datos?**
No. La base de datos se recupera sola en el próximo inicio, y además hay
respaldos automáticos (ver sección 14) por si alguna vez hiciera falta.

**Me olvidé la contraseña de admin.**
Otro Admin puede resetearla desde Usuarios. Si no hay otro, contactá soporte técnico.

**¿Puedo usarlo desde otra computadora?**
El sistema corre local en esta PC. Publicarlo en red o internet es una
configuración aparte (consultá soporte técnico).

**¿Un cliente puede ver los datos de otro?**
No: cada cliente ve solo sus pedidos y facturas; las pantallas de
administración exigen rol Admin o Cajero.

## 16. Puesta en marcha comercial

El modo local y sus accesos directos siguen siendo la opción predeterminada.
El perfil Docker `commercial` empaqueta el frontend para operación estable,
pero **no convierte por sí solo al sistema en un servicio público ni en una
solución fiscal homologada**.

Checklist de instalación:

1. Instalar Docker con Compose y un proxy inverso capaz de administrar TLS.
2. Copiar `.env.commercial.example` como `.env.commercial`. Reemplazar los tres
   placeholders: contraseña administrativa de PostgreSQL, contraseña distinta
   para `techstore_app` y `JWT_SECRET` aleatorio de 32 caracteres o más.
3. Definir `PUBLIC_BASE_URL` y `CORS_ALLOWED_ORIGINS` con el dominio `https://`
   real. El perfil rechaza modo `dev`, Swagger habilitado, HTTP y placeholders.
4. Iniciar con
   `docker compose --env-file .env.commercial --profile commercial up -d --build`.
5. Configurar el proxy HTTPS para apuntar a `127.0.0.1:5173`. No publicar los
   puertos 5432 (PostgreSQL) ni 8090 (API) hacia Internet.
6. Cambiar de inmediato las cuentas de demostración (`admin123`,
   `cajero1234`) y crear usuarios nominales con el mínimo rol necesario.
7. Configurar copias externas cifradas de `postgres_data` y `uploads_data`,
   con retención, copia fuera del servidor y una restauración ensayada.
8. Configurar monitoreo, alertas, actualizaciones, políticas de privacidad y
   controles legales/fiscales aplicables antes de atender clientes reales.

> ⚠️ **SIFEN sigue siendo demostrativo y no homologado.** Usa un certificado
> de prueba y no transmite a la DNIT. Para emitir comprobantes electrónicos con
> validez legal hacen falta habilitación, timbrado y certificado válidos, además
> de integrar y homologar los web services oficiales. No se debe vender como
> facturación electrónica operativa mientras esos pasos no estén completos.
