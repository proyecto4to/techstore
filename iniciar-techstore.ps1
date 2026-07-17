# Lanzador de TechStore: levanta PostgreSQL, backend y frontend, y abre la tienda.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
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
    & $pgCtl -D $pgData -l $pgLog -w -t 60 start *> $null
    Write-Host ' listo' -ForegroundColor Green
} else {
    Write-Host '  [1/3] PostgreSQL ya estaba corriendo' -ForegroundColor Green
}

# 2) Backend
if (-not (Test-Url 'http://localhost:8080/actuator/health')) {
    Write-Host '  [2/3] Iniciando backend (Spring Boot)...' -NoNewline
    Start-Process -FilePath 'java' `
        -ArgumentList '-jar', "$root\techstore-backend\target\techstore-backend-1.0.0.jar" `
        -WorkingDirectory "$root\techstore-backend" -WindowStyle Minimized
    $intentos = 0
    while (-not (Test-Url 'http://localhost:8080/actuator/health') -and $intentos -lt 40) {
        Start-Sleep -Seconds 3; $intentos++
    }
    if ($intentos -ge 40) { Write-Host ' NO RESPONDE (ver consola de java)' -ForegroundColor Red }
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
