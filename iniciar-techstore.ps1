# Lanzador local/portable: PostgreSQL, JAR del backend, Vite y navegador.
param(
    [switch]$PermitirRedLocal,
    # Las notificaciones push salen apagadas: en una máquina de desarrollo no se
    # quiere despertar el teléfono de nadie por cada prueba. Con esto se enciende
    # el proveedor de Expo para probarlas de verdad en un dispositivo.
    [switch]$ConPush
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$techStoreLocal = Join-Path $env:LOCALAPPDATA 'TechStore'
$runDir = Join-Path $techStoreLocal 'run'
$pgData = Join-Path $techStoreLocal 'pgdata'
$pgLog = Join-Path $techStoreLocal 'pg.log'
$pgCtl = Join-Path $root 'tools\pgsql\bin\pg_ctl.exe'
$pgIsReady = Join-Path $root 'tools\pgsql\bin\pg_isready.exe'
$backendDir = Join-Path $root 'techstore-backend'
$frontendDir = Join-Path $root 'techstore-frontend'
$backendJar = Join-Path $backendDir 'target\techstore-backend-1.0.0.jar'

Write-Host ''
Write-Host '  ============================' -ForegroundColor DarkYellow
Write-Host '     TechStore - Iniciando' -ForegroundColor DarkYellow
Write-Host '  ============================' -ForegroundColor DarkYellow
Write-Host ''

function Fallar($mensaje) {
    Write-Host "  ERROR: $mensaje" -ForegroundColor Red
    Write-Host '  No se modificaron procesos ajenos.' -ForegroundColor DarkGray
    exit 1
}

function Test-Url($url) {
    try { Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3 | Out-Null; return $true }
    catch { return $false }
}

function Get-PidPuerto([int]$puerto) {
    $conexion = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue |
        Sort-Object OwningProcess | Select-Object -First 1
    if ($null -eq $conexion) { return $null }
    return [int]$conexion.OwningProcess
}

function Test-ProcesoTechStore([int]$idProceso, $tipo) {
    try {
        $proceso = Get-Process -Id $idProceso -ErrorAction Stop
        $cim = Get-CimInstance Win32_Process -Filter "ProcessId = $idProceso" -ErrorAction Stop
        $linea = [string]$cim.CommandLine
        if ($tipo -eq 'backend') {
            return ($proceso.ProcessName -in @('java', 'javaw') -and
                $linea -match 'techstore-backend-.*\.jar' -and
                $linea -match '-Dtechstore\.launcher=local-portable')
        }
        if ($tipo -eq 'frontend') {
            return ($proceso.ProcessName -eq 'node' -and
                $linea -match '[\\/]vite[\\/]bin[\\/]vite\.js' -and
                $linea.IndexOf($frontendDir, [StringComparison]::OrdinalIgnoreCase) -ge 0)
        }
        return $false
    } catch {
        return $false
    }
}

function Registrar-Proceso([int]$idProceso, $nombre) {
    $proceso = Get-Process -Id $idProceso -ErrorAction Stop
    $registro = [ordered]@{
        pid = $idProceso
        startedUtc = $proceso.StartTime.ToUniversalTime().ToString('o')
        kind = $nombre
    }
    $registro | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDir "$nombre.pid.json") -Encoding UTF8
}

function Registrar-PuertoTechStore([int]$puerto, $tipo) {
    $idProceso = Get-PidPuerto $puerto
    if ($null -ne $idProceso -and (Test-ProcesoTechStore $idProceso $tipo)) {
        Registrar-Proceso $idProceso $tipo
        return $true
    }
    return $false
}

foreach ($requerido in @($pgCtl, $pgIsReady, $backendJar)) {
    if (-not (Test-Path -LiteralPath $requerido)) { Fallar "no se encontro $requerido" }
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Fallar 'Java 21 no esta disponible en PATH.' }
if (-not (Get-Command node.exe -ErrorAction SilentlyContinue)) { Fallar 'Node.js no esta disponible en PATH.' }
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { Fallar 'Node/npm no esta disponible en PATH.' }
$versionNodeTexto = (& node.exe --version 2>$null).TrimStart('v')
$versionNode = $null
if (-not [version]::TryParse($versionNodeTexto, [ref]$versionNode) -or
    -not (($versionNode.Major -eq 20 -and $versionNode -ge [version]'20.19.0') -or $versionNode -ge [version]'22.12.0')) {
    Fallar "Vite 8 requiere Node 20.19+ o 22.12+; se encontro $versionNodeTexto"
}
if (-not (Test-Path -LiteralPath $pgData)) { Fallar "no existe la base local en $pgData" }
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$secretsLocal = Join-Path $root 'secrets.local.ps1'
if (-not (Test-Path -LiteralPath $secretsLocal)) {
    Fallar 'falta secrets.local.ps1. Copia secrets.local.ps1.ejemplo y reemplaza todos los valores.'
}
. $secretsLocal

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET) -or $env:JWT_SECRET.Length -lt 32 -or $env:JWT_SECRET -match 'reemplaza|change.?me') {
    Fallar 'JWT_SECRET falta, es corto o sigue siendo un placeholder.'
}
if ([string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_PASSWORD) -or $env:SPRING_DATASOURCE_PASSWORD.Length -lt 16 -or $env:SPRING_DATASOURCE_PASSWORD -match 'reemplaza|techstore_pass_dev') {
    Fallar 'la contraseña administrativa de PostgreSQL falta o sigue siendo insegura.'
}
if ([string]::IsNullOrWhiteSpace($env:APP_DB_PASSWORD) -or $env:APP_DB_PASSWORD.Length -lt 16 -or $env:APP_DB_PASSWORD -match 'reemplaza') {
    Fallar 'APP_DB_PASSWORD falta o es insegura. Ejecuta configurar-rol-app.ps1 una vez.'
}
if ($env:APP_DB_PASSWORD -eq $env:SPRING_DATASOURCE_PASSWORD) {
    Fallar 'APP_DB_PASSWORD debe ser distinta de la contraseña administrativa.'
}

$env:APP_UPLOADS_DIR = if ($env:APP_UPLOADS_DIR) { $env:APP_UPLOADS_DIR } else { Join-Path $techStoreLocal 'uploads' }
# El modo portable es realmente local: ni la API ni sus endpoints de
# administración deben escuchar en la red doméstica por accidente.
$env:SERVER_ADDRESS = if ($PermitirRedLocal) { '0.0.0.0' } else { '127.0.0.1' }
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
if ($PermitirRedLocal) {
    Write-Host '  [red] API habilitada temporalmente en la LAN para pruebas del dispositivo.' -ForegroundColor Yellow
}
$env:PUSH_PROVIDER = if ($ConPush) { 'expo' } else { 'none' }
if ($ConPush) {
    Write-Host '  [push] Proveedor Expo activo: las notificaciones llegan a los dispositivos registrados.' -ForegroundColor Yellow
}
New-Item -ItemType Directory -Force -Path $env:APP_UPLOADS_DIR | Out-Null
Write-Host '  [secretos] Configuracion local segura cargada.' -ForegroundColor Green

$emailLocal = Join-Path $root 'email.local.ps1'
if (Test-Path -LiteralPath $emailLocal) {
    . $emailLocal
    Write-Host '  [correo] Credenciales SMTP cargadas: envio real activo.' -ForegroundColor Green
}

# 1) PostgreSQL portable. Solo se administra el clúster de TechStore.
& $pgCtl -D $pgData status *> $null
$postgresActivo = ($LASTEXITCODE -eq 0)
if (-not $postgresActivo) {
    $ocupantePg = Get-PidPuerto 5432
    if ($null -ne $ocupantePg) { Fallar "el puerto 5432 ya pertenece al PID $ocupantePg" }
    Write-Host '  [1/3] Iniciando PostgreSQL...' -NoNewline
    Start-Process -FilePath $pgCtl -ArgumentList "-D `"$pgData`" -l `"$pgLog`" start" -WindowStyle Hidden
    for ($i = 0; $i -lt 30; $i++) {
        & $pgCtl -D $pgData status *> $null
        if ($LASTEXITCODE -eq 0) { $postgresActivo = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $postgresActivo) { Write-Host ' fallo' -ForegroundColor Red; Fallar 'PostgreSQL no arranco; revisa pg.log.' }
    Write-Host ' listo' -ForegroundColor Green
} else {
    Write-Host '  [1/3] PostgreSQL ya estaba corriendo' -ForegroundColor Green
}

Write-Host '        Esperando conexiones...' -NoNewline
$baseLista = $false
for ($i = 0; $i -lt 30; $i++) {
    & $pgIsReady -h localhost -p 5432 -d techstore_db *> $null
    if ($LASTEXITCODE -eq 0) { $baseLista = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $baseLista) { Write-Host ' sin respuesta' -ForegroundColor Red; Fallar 'PostgreSQL no acepta conexiones.' }
Write-Host ' lista' -ForegroundColor Green

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'backup-techstore.ps1') -Silencioso
if ($LASTEXITCODE -ne 0) { Fallar 'el respaldo automatico no pudo validarse; no se iniciara la aplicacion.' }
Write-Host '  [backup] Respaldo automatico verificado.' -ForegroundColor Green

# Flyway migra como administrador; el runtime usa techstore_app sin BYPASSRLS.
$env:FLYWAY_DB_USERNAME = if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { 'techstore_user' }
$env:FLYWAY_DB_PASSWORD = $env:SPRING_DATASOURCE_PASSWORD
$env:SPRING_DATASOURCE_USERNAME = 'techstore_app'
$env:SPRING_DATASOURCE_PASSWORD = $env:APP_DB_PASSWORD
Write-Host '  [base] Runtime sin superusuario; Flyway conserva el rol migrador.' -ForegroundColor Green

# 2) Backend JAR. Nunca se mata un ocupante desconocido del puerto.
if (Test-Url 'http://localhost:8090/actuator/health') {
    $registrado = Registrar-PuertoTechStore 8090 'backend'
    Write-Host '  [2/3] Backend ya estaba corriendo' -ForegroundColor Green
    if (-not $registrado) { Write-Host '        Proceso no registrado: el cierre no lo detendra.' -ForegroundColor Yellow }
} else {
    $ocupanteBackend = Get-PidPuerto 8090
    if ($null -ne $ocupanteBackend) { Fallar "el puerto 8090 ya pertenece al PID $ocupanteBackend" }
    Write-Host '  [2/3] Iniciando backend (Spring Boot)...' -NoNewline
    $procesoBackend = Start-Process -FilePath 'java' -ArgumentList '-Dtechstore.launcher=local-portable', '-jar', 'target\techstore-backend-1.0.0.jar' `
        -WorkingDirectory $backendDir -WindowStyle Minimized -PassThru
    Registrar-Proceso $procesoBackend.Id 'backend'
    $backendListo = $false
    for ($i = 0; $i -lt 70; $i++) {
        if (Test-Url 'http://localhost:8090/actuator/health') { $backendListo = $true; break }
        if ($procesoBackend.HasExited) { break }
        Start-Sleep -Seconds 3
    }
    if (-not $backendListo) {
        Write-Host ' no responde' -ForegroundColor Red
        Stop-Process -Id $procesoBackend.Id -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath (Join-Path $runDir 'backend.pid.json') -Force -ErrorAction SilentlyContinue
        Fallar 'el backend no inicio; revisa la consola de Java.'
    }
    Write-Host ' listo' -ForegroundColor Green
}

# 3) Frontend Vite, manteniendo el flujo usado por los accesos directos.
if (Test-Url 'http://localhost:5173') {
    $registrado = Registrar-PuertoTechStore 5173 'frontend'
    Write-Host '  [3/3] Frontend ya estaba corriendo' -ForegroundColor Green
    if (-not $registrado) { Write-Host '        Proceso no registrado: el cierre no lo detendra.' -ForegroundColor Yellow }
} else {
    $ocupanteFrontend = Get-PidPuerto 5173
    if ($null -ne $ocupanteFrontend) { Fallar "el puerto 5173 ya pertenece al PID $ocupanteFrontend" }
    Write-Host '  [3/3] Iniciando frontend (Vite)...' -NoNewline
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/d', '/c', 'npm run dev' `
        -WorkingDirectory $frontendDir -WindowStyle Minimized
    $frontendListo = $false
    for ($i = 0; $i -lt 30; $i++) {
        if (Test-Url 'http://localhost:5173') { $frontendListo = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $frontendListo) { Write-Host ' no responde' -ForegroundColor Red; Fallar 'el frontend no inicio.' }
    if (-not (Registrar-PuertoTechStore 5173 'frontend')) {
        Write-Host ' listo, sin PID verificable' -ForegroundColor Yellow
        Write-Host '        El cierre no detendra un proceso que no pueda identificar.' -ForegroundColor Yellow
    } else {
        Write-Host ' listo' -ForegroundColor Green
    }
}

Write-Host ''
Write-Host '  Tienda:  http://localhost:5173' -ForegroundColor Cyan
Write-Host '  API:     http://localhost:8090' -ForegroundColor Cyan
Write-Host '  Swagger: http://localhost:8090/swagger-ui.html' -ForegroundColor Cyan
Write-Host ''
Start-Process 'http://localhost:5173'
Start-Sleep -Seconds 3
exit 0
