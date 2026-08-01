# Lanzador de TechStore: levanta PostgreSQL, backend y frontend, y abre la tienda.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# Credenciales SMTP opcionales para el ENVIO REAL de correos (archivo local, ignorado por git).
# Copia 'email.local.ps1.ejemplo' como 'email.local.ps1' y poné tu casilla + contraseña de aplicacion.
$emailLocal = Join-Path $root 'email.local.ps1'
if (Test-Path $emailLocal) {
    . $emailLocal
    Write-Host '  [correo] Credenciales SMTP cargadas: envio de correos REAL activo.' -ForegroundColor Green
}

# Secretos de JWT y base de datos (archivo local, ignorado por git).
# Copia 'secrets.local.ps1.ejemplo' como 'secrets.local.ps1' con tus propios valores.
$secretsLocal = Join-Path $root 'secrets.local.ps1'
if (Test-Path $secretsLocal) {
    . $secretsLocal
    Write-Host '  [secretos] JWT_SECRET y password de BD cargados desde secrets.local.ps1.' -ForegroundColor Green
} else {
    Write-Host '  [secretos] AVISO: secrets.local.ps1 no existe, usando valores por defecto (solo para pruebas).' -ForegroundColor Yellow
}

$pgData = "$env:LOCALAPPDATA\TechStore\pgdata"
$pgLog = "$env:LOCALAPPDATA\TechStore\pg.log"
$pgCtl = Join-Path $root 'tools\pgsql\bin\pg_ctl.exe'

Write-Host ''
Write-Host '  ============================' -ForegroundColor DarkYellow
Write-Host '     TechStore - Iniciando'    -ForegroundColor DarkYellow
Write-Host '  ============================' -ForegroundColor DarkYellow
Write-Host ''

function Test-Url($url) {
    try { Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3 | Out-Null; $true } catch { $false }
}

# 1) PostgreSQL
& $pgCtl -D $pgData status *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host '  [1/3] Iniciando PostgreSQL...' -NoNewline
    # Start-Process sin -Wait a proposito: en PowerShell 5.1, "&" con
    # redireccion queda esperando la tuberia que hereda postgres, y -Wait
    # espera al arbol completo de procesos (postgres incluido). El sondeo
    # de estado de abajo es el que espera a que el servidor este listo.
    Start-Process -FilePath $pgCtl `
        -ArgumentList "-D `"$pgData`" -l `"$pgLog`" start" `
        -WindowStyle Hidden
    $intentosPg = 0
    do {
        & $pgCtl -D $pgData status *> $null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 2; $intentosPg++
    } while ($intentosPg -lt 15)
    if ($intentosPg -ge 15) { Write-Host ' NO ARRANCO (ver pg.log)' -ForegroundColor Red }
    else { Write-Host ' listo' -ForegroundColor Green }
} else {
    Write-Host '  [1/3] PostgreSQL ya estaba corriendo' -ForegroundColor Green
}

# El postmaster puede estar "corriendo" pero aun en recuperacion, rechazando
# conexiones; si el backend arranca en ese momento, muere. pg_isready espera
# a que la base acepte conexiones de verdad.
$pgIsReady = Join-Path $root 'tools\pgsql\bin\pg_isready.exe'
Write-Host '        Esperando a que la base acepte conexiones...' -NoNewline
$intentosListo = 0
do {
    & $pgIsReady -h localhost -p 5432 *> $null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Seconds 2; $intentosListo++
} while ($intentosListo -lt 30)
if ($intentosListo -ge 30) { Write-Host ' SIN RESPUESTA' -ForegroundColor Red }
else { Write-Host ' lista' -ForegroundColor Green }

# Respaldo automatico al arrancar: la base ya se recupero y acepta
# conexiones, asi que este dump es de un estado sano.
& (Join-Path $root 'backup-techstore.ps1')

# La aplicacion corre con un rol SIN privilegios de administrador: asi las
# politicas de Row Level Security se le aplican de verdad, y una eventual
# inyeccion SQL no llega al sistema operativo.
# Flyway se queda con el rol administrador, que necesita para crear y alterar
# tablas y para ver todas las empresas al migrar.
# Va DESPUES del respaldo a proposito: backup-techstore.ps1 vuelve a cargar
# secrets.local.ps1 y dejaria SPRING_DATASOURCE_PASSWORD con la clave del
# administrador mientras el usuario ya es techstore_app.
# Si todavia no se corrio configurar-rol-app.ps1, todo sigue como antes.
if ($env:APP_DB_PASSWORD) {
    $env:FLYWAY_DB_USERNAME = if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { 'techstore_user' }
    $env:FLYWAY_DB_PASSWORD = $env:SPRING_DATASOURCE_PASSWORD
    $env:SPRING_DATASOURCE_USERNAME = 'techstore_app'
    $env:SPRING_DATASOURCE_PASSWORD = $env:APP_DB_PASSWORD
    Write-Host '  [base] La app conecta como techstore_app (sin superusuario); Flyway migra como administrador.' -ForegroundColor Green
} else {
    Write-Host '  [base] AVISO: la app conecta como superusuario. Corre configurar-rol-app.ps1 para cerrarlo.' -ForegroundColor Yellow
}

# 2) Backend
if (-not (Test-Url 'http://localhost:8080/actuator/health')) {
    Write-Host '  [2/3] Iniciando backend (Spring Boot)...' -NoNewline
    # Si quedo un java colgado en el puerto 8080 (p. ej. sin base de datos), se baja primero.
    $ocupantes = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    foreach ($idProc in ($ocupantes.OwningProcess | Sort-Object -Unique)) {
        try {
            if ((Get-Process -Id $idProc -ErrorAction Stop).ProcessName -eq 'java') {
                Stop-Process -Id $idProc -Force
            }
        } catch { }
    }
    # Ruta relativa a proposito: PowerShell 5.1 no agrega comillas a argumentos
    # con espacios y esta ruta de usuario los tiene ("Dell Vostro").
    Start-Process -FilePath 'java' `
        -ArgumentList '-jar', 'target\techstore-backend-1.0.0.jar' `
        -WorkingDirectory "$root\techstore-backend" -WindowStyle Minimized
    $intentos = 0
    while (-not (Test-Url 'http://localhost:8080/actuator/health') -and $intentos -lt 70) {
        Start-Sleep -Seconds 3; $intentos++
    }
    if ($intentos -ge 70) { Write-Host ' NO RESPONDE (ver consola de java)' -ForegroundColor Red }
    else { Write-Host ' listo' -ForegroundColor Green }
} else {
    Write-Host '  [2/3] Backend ya estaba corriendo' -ForegroundColor Green
}

# 3) Frontend
if (-not (Test-Url 'http://localhost:5173')) {
    Write-Host '  [3/3] Iniciando frontend (Vite)...' -NoNewline
    Start-Process -FilePath 'cmd' -ArgumentList '/c', 'npm run dev' `
        -WorkingDirectory "$root\techstore-frontend" -WindowStyle Minimized
    $intentos = 0
    while (-not (Test-Url 'http://localhost:5173') -and $intentos -lt 20) {
        Start-Sleep -Seconds 2; $intentos++
    }
    if ($intentos -ge 20) { Write-Host ' NO RESPONDE' -ForegroundColor Red }
    else { Write-Host ' listo' -ForegroundColor Green }
} else {
    Write-Host '  [3/3] Frontend ya estaba corriendo' -ForegroundColor Green
}

Write-Host ''
Write-Host '  Tienda:  http://localhost:5173'  -ForegroundColor Cyan
Write-Host '  API:     http://localhost:8080'  -ForegroundColor Cyan
Write-Host '  Swagger: http://localhost:8080/swagger-ui.html' -ForegroundColor Cyan
Write-Host ''
Start-Process 'http://localhost:5173'
Start-Sleep -Seconds 3
