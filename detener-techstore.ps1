# Detiene los tres servicios de TechStore (frontend, backend y PostgreSQL).
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$pgData = "$env:LOCALAPPDATA\TechStore\pgdata"
$pgCtl = Join-Path $root 'tools\pgsql\bin\pg_ctl.exe'

Write-Host 'Deteniendo TechStore...'

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

& $pgCtl -D $pgData status *> $null
if ($LASTEXITCODE -eq 0) {
    Write-Host '  Deteniendo PostgreSQL...'
    & $pgCtl -D $pgData stop *> $null
}

Write-Host 'Listo.'
Start-Sleep -Seconds 2
