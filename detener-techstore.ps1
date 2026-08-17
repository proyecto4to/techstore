# Detiene solo los procesos que fueron registrados por iniciar-techstore.ps1.
param(
    [switch]$SinBackup,
    [switch]$MantenerPostgres,
    [switch]$NoEspera
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$techStoreLocal = Join-Path $env:LOCALAPPDATA 'TechStore'
$runDir = Join-Path $techStoreLocal 'run'
$pgData = Join-Path $techStoreLocal 'pgdata'
$pgCtl = Join-Path $root 'tools\pgsql\bin\pg_ctl.exe'
$frontendDir = Join-Path $root 'techstore-frontend'

function Get-ProcesoRegistrado($archivoPid, $tipo) {
    if (-not (Test-Path -LiteralPath $archivoPid)) { return $null }
    try {
        $registro = Get-Content -LiteralPath $archivoPid -Raw | ConvertFrom-Json
        $proceso = Get-Process -Id ([int]$registro.pid) -ErrorAction Stop
        $inicioGuardado = [datetime]::Parse([string]$registro.startedUtc).ToUniversalTime()
        if ([math]::Abs(($proceso.StartTime.ToUniversalTime() - $inicioGuardado).TotalSeconds) -gt 2) {
            return $null
        }

        $cim = Get-CimInstance Win32_Process -Filter "ProcessId = $($proceso.Id)" -ErrorAction Stop
        $linea = [string]$cim.CommandLine
        if ($tipo -eq 'backend') {
            if ($proceso.ProcessName -notin @('java', 'javaw') -or
                $linea -notmatch 'techstore-backend-.*\.jar' -or
                $linea -notmatch '-Dtechstore\.launcher=local-portable') { return $null }
        } elseif ($tipo -eq 'frontend') {
            if ($proceso.ProcessName -ne 'node' -or
                $linea -notmatch '[\\/]vite[\\/]bin[\\/]vite\.js' -or
                $linea.IndexOf($frontendDir, [StringComparison]::OrdinalIgnoreCase) -lt 0) { return $null }
        } else {
            return $null
        }
        return $proceso
    } catch {
        return $null
    }
}

function Detener-ProcesoRegistrado($nombre, $tipo) {
    $archivoPid = Join-Path $runDir "$nombre.pid.json"
    $proceso = Get-ProcesoRegistrado $archivoPid $tipo
    if ($null -eq $proceso) {
        if (Test-Path -LiteralPath $archivoPid) { Remove-Item -LiteralPath $archivoPid -Force }
        Write-Host "  ${nombre}: no hay un proceso TechStore registrado." -ForegroundColor DarkGray
        return
    }

    Write-Host "  Deteniendo $nombre (PID $($proceso.Id))..."
    Stop-Process -Id $proceso.Id -ErrorAction SilentlyContinue
    for ($i = 0; $i -lt 10; $i++) {
        if (-not (Get-Process -Id $proceso.Id -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Milliseconds 500
    }
    $aunVivo = Get-ProcesoRegistrado $archivoPid $tipo
    if ($null -ne $aunVivo) { Stop-Process -Id $aunVivo.Id -Force -ErrorAction SilentlyContinue }
    Remove-Item -LiteralPath $archivoPid -Force -ErrorAction SilentlyContinue
}

Write-Host 'Deteniendo TechStore...'
Detener-ProcesoRegistrado 'frontend' 'frontend'
Detener-ProcesoRegistrado 'backend' 'backend'

if (-not (Test-Path -LiteralPath $pgCtl)) {
    Write-Host "  ERROR: no se encontro pg_ctl en $pgCtl" -ForegroundColor Red
    exit 1
}

& $pgCtl -D $pgData status *> $null
$postgresActivo = ($LASTEXITCODE -eq 0)
if ($postgresActivo -and -not $SinBackup) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'backup-techstore.ps1') -Silencioso
    $codigoBackup = $LASTEXITCODE
    if ($codigoBackup -ne 0) {
        Write-Host '  ERROR: el respaldo fallo. PostgreSQL queda encendido para no perder datos.' -ForegroundColor Red
        exit 1
    }
    Write-Host '  Respaldo de cierre verificado.' -ForegroundColor Green
}

if ($postgresActivo -and -not $MantenerPostgres) {
    Write-Host '  Deteniendo PostgreSQL...'
    & $pgCtl -D $pgData stop -m fast -w -t 60 *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host '  ERROR: PostgreSQL no se pudo detener limpiamente.' -ForegroundColor Red
        exit 1
    }
}

Write-Host 'Listo.'
if (-not $NoEspera) { Start-Sleep -Seconds 2 }
exit 0
