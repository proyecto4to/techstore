param(
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'

$projectPath = 'C:\Users\Dell Vostro\OneDrive\Documentos\GitHub\techstore\techstore-mobile'
$previewUrl = 'http://localhost:8081'
$expoLogDirectory = Join-Path $projectPath '.expo'
$stdoutLog = Join-Path $expoLogDirectory 'shortcut-stdout.log'
$stderrLog = Join-Path $expoLogDirectory 'shortcut-stderr.log'

function Get-MobileListener {
    Get-NetTCPConnection -State Listen -LocalPort 8081 -ErrorAction SilentlyContinue |
        Select-Object -First 1
}

function Test-MobileReady {
    try {
        $response = Invoke-WebRequest -Uri $previewUrl -UseBasicParsing -TimeoutSec 3
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Show-MobileError([string]$message) {
    Add-Type -AssemblyName PresentationFramework
    [System.Windows.MessageBox]::Show($message, 'TechStore Mobile') | Out-Null
}

$expoRunning = Get-MobileListener
if ($expoRunning -and -not (Test-MobileReady)) {
    $listenerProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($expoRunning.OwningProcess)"
    $isTechStoreExpo = $listenerProcess.Name -eq 'node.exe' -and
        $listenerProcess.CommandLine -like '*techstore-mobile*' -and
        $listenerProcess.CommandLine -like '*expo*start*web*'

    if (-not $isTechStoreExpo) {
        Show-MobileError "El puerto 8081 está ocupado por otro programa. Cerralo o cambiá el puerto antes de abrir TechStore Mobile."
        exit 1
    }

    Stop-Process -Id $expoRunning.OwningProcess -Force
    $deadline = (Get-Date).AddSeconds(10)
    do {
        Start-Sleep -Milliseconds 250
        $expoRunning = Get-MobileListener
    } while ($expoRunning -and (Get-Date) -lt $deadline)
}

if (-not (Test-MobileReady)) {
    New-Item -ItemType Directory -Path $expoLogDirectory -Force | Out-Null
    Start-Process -FilePath 'npm.cmd' `
        -ArgumentList @('run', 'web') `
        -WorkingDirectory $projectPath `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -WindowStyle Hidden

    $deadline = (Get-Date).AddSeconds(90)
    do {
        Start-Sleep -Milliseconds 500
        $ready = Test-MobileReady
    } while (-not $ready -and (Get-Date) -lt $deadline)
}

if (-not (Test-MobileReady)) {
    Show-MobileError "No se pudo iniciar la vista móvil. Revisá los logs:`n$stdoutLog`n$stderrLog"
    exit 1
}

if (-not $NoBrowser) {
    Start-Process $previewUrl
}
