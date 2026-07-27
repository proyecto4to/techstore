# Restaura la base de datos de TechStore desde un respaldo.
# Muestra los respaldos disponibles, deja elegir uno y lo restaura sobre
# la base actual (reemplazando su contenido). El backend debe estar
# detenido para evitar conflictos de conexiones.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$secretsLocal = Join-Path $root 'secrets.local.ps1'
if (Test-Path $secretsLocal) { . $secretsLocal }

$pgRestore = Join-Path $root 'tools\pgsql\bin\pg_restore.exe'
$pgCtl = Join-Path $root 'tools\pgsql\bin\pg_ctl.exe'
$carpeta = "$env:LOCALAPPDATA\TechStore\backups"
$pgData = "$env:LOCALAPPDATA\TechStore\pgdata"

Write-Host ''
Write-Host '  ============================' -ForegroundColor DarkYellow
Write-Host '    TechStore - Restaurar' -ForegroundColor DarkYellow
Write-Host '  ============================' -ForegroundColor DarkYellow
Write-Host ''

$backups = @(Get-ChildItem $carpeta -Filter 'techstore-*.dump' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending)
if ($backups.Count -eq 0) {
    Write-Host '  No hay respaldos disponibles en:' -ForegroundColor Red
    Write-Host "    $carpeta"
    Read-Host 'Enter para salir'
    exit 1
}

Write-Host '  Respaldos disponibles (el mas reciente primero):' -ForegroundColor Cyan
for ($i = 0; $i -lt $backups.Count; $i++) {
    $b = $backups[$i]
    Write-Host ("    [{0}] {1}   ({2} KB)" -f $i, $b.LastWriteTime.ToString('dd/MM/yyyy HH:mm'), [math]::Round($b.Length / 1KB))
}
Write-Host ''

$sel = Read-Host '  Numero del respaldo a restaurar (Enter = el mas reciente, X = cancelar)'
if ($sel -eq 'X' -or $sel -eq 'x') { Write-Host '  Cancelado.'; exit 0 }
if ([string]::IsNullOrWhiteSpace($sel)) { $sel = 0 }

$indice = 0
if (-not [int]::TryParse($sel, [ref]$indice) -or $indice -lt 0 -or $indice -ge $backups.Count) {
    Write-Host '  Seleccion invalida.' -ForegroundColor Red
    exit 1
}
$elegido = $backups[$indice]

Write-Host ''
Write-Host "  Vas a REEMPLAZAR el contenido actual de la base con el respaldo del" -ForegroundColor Yellow
Write-Host ("  {0}. Esto no se puede deshacer." -f $elegido.LastWriteTime.ToString('dd/MM/yyyy HH:mm')) -ForegroundColor Yellow
$confirma = Read-Host '  Escribi SI para continuar'
if ($confirma -ne 'SI') { Write-Host '  Cancelado.'; exit 0 }

# La base debe estar corriendo; el backend, detenido.
$ocupantes = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
foreach ($idProc in ($ocupantes.OwningProcess | Sort-Object -Unique)) {
    try {
        if ((Get-Process -Id $idProc -ErrorAction Stop).ProcessName -eq 'java') {
            Write-Host '  Deteniendo el backend antes de restaurar...'
            Stop-Process -Id $idProc -Force
        }
    } catch { }
}

& $pgCtl -D $pgData status *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host '  Iniciando PostgreSQL...'
    & $pgCtl -D $pgData -l "$env:LOCALAPPDATA\TechStore\pg.log" -w -t 60 start *> $null
    Start-Sleep -Seconds 3
}

Write-Host '  Restaurando...' -ForegroundColor DarkYellow
$env:PGPASSWORD = if ($env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD } else { 'techstore_pass_dev' }
# --clean --if-exists: borra los objetos actuales antes de recrearlos.
& $pgRestore -h localhost -p 5432 -U techstore_user -d techstore_db --clean --if-exists --no-owner $elegido.FullName 2>&1 |
    Where-Object { $_ -notmatch 'does not exist, skipping' }

Write-Host ''
Write-Host '  Restauracion completada. Volve a iniciar TechStore con su acceso directo.' -ForegroundColor Green
Read-Host '  Enter para salir'
