# Detiene los tres servicios de TechStore (frontend, backend y PostgreSQL).
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$pgData = "$env:LOCALAPPDATA\TechStore\pgdata"
$pgCtl = Join-Path $root 'tools\pgsql\bin\pg_ctl.exe'

Write-Host 'Deteniendo TechStore...'

# Detener el backend/frontend primero (liberan conexiones a la base).
foreach ($puerto in 5173, 8080) {
    $conexiones = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue
    foreach ($pid_ in ($conexiones.OwningProcess | Sort-Object -Unique)) {
        try {
            $proc = Get-Process -Id $pid_ -ErrorAction Stop
            Write-Host "  Puerto $puerto -> deteniendo $($proc.ProcessName) (PID $pid_)"
            Stop-Process -Id $pid_ -Force -Confirm:$false
        } catch { }
    }
}

# Respaldo del cierre de la jornada, con la base todavia encendida.
& $pgCtl -D $pgData status *> $null
if ($LASTEXITCODE -eq 0) {
    & (Join-Path $root 'backup-techstore.ps1')
    Write-Host '  Deteniendo PostgreSQL...'
    & $pgCtl -D $pgData stop *> $null
}

Write-Host 'Listo.'
Start-Sleep -Seconds 2
