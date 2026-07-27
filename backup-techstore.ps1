# Respalda la base de datos de TechStore con pg_dump.
# Guarda un archivo comprimido en %LOCALAPPDATA%\TechStore\backups y
# conserva solo los ultimos $MAX_BACKUPS (rotacion). Los respaldos viven
# fuera de OneDrive a proposito, igual que el data directory.
param(
    [switch]$Silencioso  # sin mensajes; para llamarlo desde otros scripts
)

$ErrorActionPreference = 'Stop'
$MAX_BACKUPS = 20

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$secretsLocal = Join-Path $root 'secrets.local.ps1'
if (Test-Path $secretsLocal) { . $secretsLocal }

$pgDump = Join-Path $root 'tools\pgsql\bin\pg_dump.exe'
$carpeta = "$env:LOCALAPPDATA\TechStore\backups"
New-Item -ItemType Directory -Force $carpeta | Out-Null

$sello = Get-Date -Format 'yyyyMMdd-HHmmss'
$destino = Join-Path $carpeta "techstore-$sello.dump"

function Escribir($texto, $color = 'Gray') {
    if (-not $Silencioso) { Write-Host $texto -ForegroundColor $color }
}

Escribir "  Respaldando base de datos..." 'DarkYellow'
$env:PGPASSWORD = if ($env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD } else { 'techstore_pass_dev' }
try {
    # -Fc = formato custom comprimido (se restaura con pg_restore).
    & $pgDump -h localhost -p 5432 -U techstore_user -Fc -f $destino techstore_db
    if ($LASTEXITCODE -ne 0) { throw "pg_dump devolvio $LASTEXITCODE" }
} catch {
    Escribir "  ERROR: no se pudo respaldar ($_). Verifica que la base este corriendo." 'Red'
    exit 1
}

$kb = [math]::Round((Get-Item $destino).Length / 1KB)
Escribir "  Respaldo listo: $destino ($kb KB)" 'Green'

# Rotacion: dejar solo los mas recientes.
$viejos = Get-ChildItem $carpeta -Filter 'techstore-*.dump' |
    Sort-Object LastWriteTime -Descending | Select-Object -Skip $MAX_BACKUPS
foreach ($f in $viejos) {
    Remove-Item $f.FullName -Force
    Escribir "  Rotacion: eliminado respaldo viejo $($f.Name)" 'DarkGray'
}
