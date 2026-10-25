# Levanta TechStore y lo publica en internet con una direccion fija y HTTPS.
#
# El lanzador normal deja la API atada a la red de casa: sirve para probar desde
# el mismo WiFi, pero la aplicacion movil no llega desde afuera y la direccion
# cambia cada vez que el router reparte otra IP. Este script agrega el tunel, que
# da una direccion publica que no cambia.
#
# La computadora tiene que quedar encendida: esto publica el backend que corre
# aca, no lo reemplaza por un servidor.
param(
    [switch]$SinPush
)

$ErrorActionPreference = 'Continue'
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$ngrok = Join-Path $env:LOCALAPPDATA 'ngrok-bin\ngrok.exe'
$dominio = 'closure-unrigged-postcard.ngrok-free.dev'
$logTunel = Join-Path $env:LOCALAPPDATA 'TechStore\tunel.log'

if (-not (Test-Path -LiteralPath $ngrok)) {
    Write-Host "  ERROR: falta ngrok en $ngrok" -ForegroundColor Red
    Write-Host '  Descargalo de https://ngrok.com/download y descomprimilo ahi.' -ForegroundColor DarkGray
    exit 1
}

# Primero el sistema; sin backend arriba el tunel publicaria una puerta cerrada.
# Las opciones van en una tabla: pasadas como texto dentro de un arreglo
# ('-ConPush'), PowerShell no las toma como switches y el push quedaba apagado
# sin aviso. La API sigue escuchando solo en esta PC: ngrok llega por
# localhost, asi que no hace falta abrirla a la red de casa.
$opciones = @{}
if (-not $SinPush) { $opciones.ConPush = $true }
& (Join-Path $raiz 'iniciar-techstore.ps1') @opciones
if ($LASTEXITCODE -ne 0) {
    Write-Host '  El sistema no arranco; no se abre el tunel.' -ForegroundColor Red
    exit 1
}

# Un tunel viejo de TechStore mantiene tomada la direccion y el nuevo no puede
# usarla. Se cierra solo ese, con el mismo criterio que detener-techstore.ps1:
# otro ngrok del equipo -el de otro proyecto, con su propia cuenta- sigue
# andando.
Get-Process ngrok -ErrorAction SilentlyContinue | Where-Object {
    $linea = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
    [string]$linea -match 'http\s+8090'
} | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

Write-Host ''
Write-Host '  [publico] Abriendo el tunel...' -NoNewline
New-Item -ItemType Directory -Force -Path (Split-Path $logTunel) | Out-Null
Start-Process -FilePath $ngrok `
    -ArgumentList @('http', '8090', "--url=https://$dominio", '--log', 'stdout') `
    -RedirectStandardOutput $logTunel -WindowStyle Hidden

$listo = $false
$limite = (Get-Date).AddSeconds(45)
do {
    Start-Sleep -Seconds 2
    try {
        $r = Invoke-WebRequest -Uri "https://$dominio/actuator/health" -UseBasicParsing -TimeoutSec 8
        if ($r.StatusCode -eq 200) { $listo = $true }
    } catch { }
} while (-not $listo -and (Get-Date) -lt $limite)

if ($listo) {
    Write-Host ' listo' -ForegroundColor Green
    Write-Host ''
    Write-Host "  Direccion publica: https://$dominio" -ForegroundColor Cyan
    Write-Host '  La aplicacion movil ya funciona con datos moviles, fuera de esta red.' -ForegroundColor DarkGray
} else {
    Write-Host ' fallo' -ForegroundColor Red
    Write-Host "  El tunel no respondio. Revisa $logTunel" -ForegroundColor DarkGray
}
