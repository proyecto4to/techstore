param(
    [Parameter(Mandatory = $true)] [string]$Email,
    [Parameter(Mandatory = $true)] [string]$Password,
    [string]$ProductQuery = 'Cable HDMI',
    [int]$ProductId = 32
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $root 'artifacts\tools\android-platform-tools\platform-tools\adb.exe'
$evidence = Join-Path $root 'artifacts\device-e2e\run'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

function Adb([Parameter(ValueFromRemainingArguments = $true)] [string[]]$Arguments) {
    & $adb @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB falló: $($Arguments -join ' ')" }
}

function Dump([string]$Name) {
    Adb shell uiautomator dump /sdcard/techstore-e2e.xml | Out-Null
    Adb pull /sdcard/techstore-e2e.xml (Join-Path $evidence "$Name.xml") | Out-Null
    & $adb exec-out screencap -p > (Join-Path $evidence "$Name.png")
    [xml](Get-Content -LiteralPath (Join-Path $evidence "$Name.xml"))
}

function Find-Node([xml]$Ui, [string]$Value) {
    @($Ui.SelectNodes('//node') | Where-Object {
        $_.text -eq $Value -or $_.'content-desc' -eq $Value -or $_.'resource-id' -eq $Value -or
        $_.'resource-id' -eq "com.techstore.mobile:id/$Value"
    }) | Sort-Object -Property @{ Expression = { if ($_.'clickable' -eq 'true') { 0 } else { 1 } } },
        @{ Expression = {
            if ($_.bounds -match '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$' -and
                [int]$Matches[3] -gt [int]$Matches[1] -and [int]$Matches[4] -gt [int]$Matches[2]) { 0 } else { 1 }
        } } | Select-Object -First 1
}

function Center($Node) {
    if ($Node.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { throw "Bounds inválidos: $($Node.bounds)" }
    [pscustomobject]@{ X = ([int]$Matches[1] + [int]$Matches[3]) / 2; Y = ([int]$Matches[2] + [int]$Matches[4]) / 2 }
}

function Wait-Node([string]$Value, [int]$TimeoutSeconds = 20) {
    $limit = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $ui = Dump 'current'
        $node = Find-Node $ui $Value
        if ($node) { return $node }
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $limit)
    throw "No apareció '$Value'"
}

function Tap([string]$Value, [int]$TimeoutSeconds = 20) {
    $tabs = @{ Inicio = 108; Buscar = 324; Carrito = 540; Pedidos = 756; Cuenta = 972 }
    if ($tabs.ContainsKey($Value)) {
        Adb shell input tap $tabs[$Value] 2200 | Out-Null
        Start-Sleep -Milliseconds 900
        return
    }
    $point = Center (Wait-Node $Value $TimeoutSeconds)
    Adb shell input tap ([int]$point.X) ([int]$point.Y) | Out-Null
    Start-Sleep -Milliseconds 900
}

function Enter([string]$Id, [string]$Value) {
    Tap $Id
    Adb shell input text $Value | Out-Null
    Start-Sleep -Milliseconds 500
}

function Save([string]$Name, [string]$Expected) {
    $ui = Dump $Name
    if (-not (Find-Node $ui $Expected)) { throw "Evidencia '$Name' no contiene '$Expected'" }
    "PASS $Name => $Expected"
}

if (-not (Test-Path -LiteralPath $adb)) { throw 'ADB no está disponible' }
if (-not (& $adb devices | Select-String '\sdevice$' | Select-Object -First 1)) { throw 'No hay Android autorizado' }

Adb shell am force-stop com.techstore.mobile | Out-Null
Adb shell monkey -p com.techstore.mobile -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 3
Save '01-launch' 'Inicio'

Tap 'Cuenta'
Tap 'Iniciar sesión'
Tap 'login-register'
Save '02-register' 'Crear cuenta'
Enter 'register-nombre' 'Cliente'
Enter 'register-apellido' 'E2E'
Enter 'register-email' $Email
Enter 'register-password' $Password
Enter 'register-confirm-password' $Password
Adb shell input keyevent 4 | Out-Null
Tap 'register-submit'
Wait-Node 'Mi cuenta' 25 | Out-Null
Save '03-account' 'Cliente E2E'

Tap 'Cerrar sesión'
Wait-Node 'Iniciar sesión' 20 | Out-Null
Tap 'Iniciar sesión'
Enter 'login-email' $Email
Enter 'login-password' $Password
Adb shell input keyevent 4 | Out-Null
Tap 'login-submit'
Wait-Node 'Mi cuenta' 25 | Out-Null
Save '04-login' 'Cliente E2E'

Tap 'Buscar'
Enter 'catalog-search' ($ProductQuery -replace ' ', '%s')
Tap "catalog-product-$ProductId" 25
Save '05-product' 'Guardar en favoritos'
Tap 'Guardar en favoritos'
Wait-Node 'Quitar de favoritos' 20 | Out-Null
Save '06-favorite' 'Quitar de favoritos'
Tap 'product-add-to-cart'
Wait-Node 'cart-checkout' 25 | Out-Null
Save '07-cart' 'Precio y disponibilidad verificados por TechStore'
Tap 'cart-checkout'
Wait-Node 'Dirección de entrega' 20 | Out-Null
Tap 'Agregar dirección'
Enter 'address-nombreDestinatario' 'Cliente E2E'
Enter 'address-telefono' '0981123456'
Enter 'address-departamento' 'Central'
Enter 'address-ciudad' 'San%sLorenzo'
Enter 'address-direccionLinea1' 'Avenida%sE2E%s123'
Enter 'address-referencia' 'Prueba%sautomatizada%sTechStore'
Adb shell input keyevent 4 | Out-Null
Tap 'address-save'
Wait-Node 'address-continue' 25 | Out-Null
Save '08-address' 'Cliente E2E'
Tap 'address-continue'
Wait-Node 'shipping-continue' 25 | Out-Null
$shippingUi = Dump '09-shipping'
$rate = $shippingUi.SelectNodes('//node') | Where-Object { $_.'resource-id' -like 'com.techstore.mobile:id/shipping-rate-*' } | Select-Object -First 1
if (-not $rate) { throw 'No se encontró una tarifa de envío' }
$ratePoint = Center $rate
Adb shell input tap ([int]$ratePoint.X) ([int]$ratePoint.Y) | Out-Null
Tap 'shipping-continue'
Tap 'payment-PAGO_EN_LOCAL'
Tap 'payment-continue'
Wait-Node 'checkout-confirm' 25 | Out-Null
Save '10-review' 'Confirmar pedido'

'DEVICE_E2E_READY_FOR_CHECKOUT'
