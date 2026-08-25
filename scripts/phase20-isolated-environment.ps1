param(
    [ValidateSet('Start', 'Restart', 'Cleanup')]
    [string]$Action = 'Start',
    [string]$DatabaseName,
    [int]$BackendProcessId
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$psql = Join-Path $root 'tools\pgsql\bin\psql.exe'
$defaultBackendJar = Join-Path $root 'techstore-backend\target\techstore-backend-1.0.0.jar'
$isolatedBackendJar = Join-Path $root 'techstore-backend\target\techstore-backend-1.0.0-phase20.jar'
$backendJar = if (Test-Path -LiteralPath $isolatedBackendJar) { $isolatedBackendJar } else { $defaultBackendJar }
$secretsLocal = Join-Path $root 'secrets.local.ps1'
$phaseRoot = Join-Path $env:LOCALAPPDATA 'TechStore\phase20'

function Test-DatabaseName([string]$Name) {
    return $Name -match '^techstore_phase20_\d{14}$'
}

function Invoke-Psql([string]$Database, [string]$Sql) {
    & $psql -h localhost -p 5432 -U techstore_user -d $Database -v ON_ERROR_STOP=1 -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL rechazo una operacion sobre $Database."
    }
}

function Resolve-Java21 {
    $candidates = @()
    if ($env:JAVA_HOME) {
        $candidates += Join-Path $env:JAVA_HOME 'bin\java.exe'
    }
    $fromPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($null -ne $fromPath) {
        $candidates += $fromPath.Source
    }
    $javaRoot = Join-Path $env:ProgramFiles 'Java'
    if (Test-Path -LiteralPath $javaRoot) {
        $candidates += Get-ChildItem -LiteralPath $javaRoot -Directory |
            ForEach-Object { Join-Path $_.FullName 'bin\java.exe' }
    }

    $compatible = foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $candidate)) { continue }
        $productVersion = (Get-Item -LiteralPath $candidate).VersionInfo.ProductVersion
        if ($productVersion -match '^(?<major>\d+)') {
            $major = [int]$Matches['major']
            if ($major -ge 21) {
                [pscustomobject]@{ Path = $candidate; Major = $major }
            }
        }
    }
    return $compatible | Sort-Object Major -Descending | Select-Object -First 1
}

foreach ($required in @($psql, $secretsLocal)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "No se encontro $required"
    }
}

. $secretsLocal
$adminPassword = $env:SPRING_DATASOURCE_PASSWORD
$runtimePassword = $env:APP_DB_PASSWORD
if ([string]::IsNullOrWhiteSpace($adminPassword) -or
    [string]::IsNullOrWhiteSpace($runtimePassword)) {
    throw 'Faltan las credenciales PostgreSQL locales.'
}
$env:PGPASSWORD = $adminPassword

if ($Action -eq 'Cleanup') {
    if (-not (Test-DatabaseName $DatabaseName)) {
        throw 'Cleanup rechazo un nombre que no corresponde a una base temporal de Phase 20.'
    }
    if ($BackendProcessId -le 0) {
        throw 'Cleanup requiere el PID exacto del backend aislado.'
    }

    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $BackendProcessId" -ErrorAction SilentlyContinue
    if ($null -ne $process) {
        $expectedMarker = "-Dtechstore.phase20=$DatabaseName"
        if ($process.Name -notin @('java.exe', 'javaw.exe') -or
            [string]$process.CommandLine -notlike "*$expectedMarker*" -or
            [string]$process.CommandLine -notlike '*techstore-backend-1.0.0*.jar*') {
            throw 'El PID no corresponde al backend aislado esperado; no se detuvo nada.'
        }
        Stop-Process -Id $BackendProcessId
        Wait-Process -Id $BackendProcessId -Timeout 20 -ErrorAction SilentlyContinue
    }

    $exists = & $psql -h localhost -p 5432 -U techstore_user -d postgres -tAc `
        "SELECT 1 FROM pg_database WHERE datname='$DatabaseName'"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar PostgreSQL.' }
    if ([string]$exists -match '1') {
        Invoke-Psql 'postgres' "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$DatabaseName' AND pid <> pg_backend_pid();"
        Invoke-Psql 'postgres' "DROP DATABASE $DatabaseName;"
    }
    Write-Output "PHASE20_CLEANUP_DATABASE=$DatabaseName"
    Write-Output 'PHASE20_CLEANUP=OK'
    exit 0
}

if (-not (Test-Path -LiteralPath $backendJar)) {
    throw "No se encontro $backendJar"
}
$javaRuntime = Resolve-Java21
if ($null -eq $javaRuntime) {
    throw 'Se requiere Java 21 o superior para iniciar el backend aislado.'
}
$java = $javaRuntime.Path

if ($Action -eq 'Restart') {
    if (-not (Test-DatabaseName $DatabaseName)) {
        throw 'Restart rechazo un nombre que no corresponde a una base temporal de Phase 20.'
    }
    if ($BackendProcessId -le 0) {
        throw 'Restart requiere el PID exacto del backend aislado.'
    }
    $running = Get-CimInstance Win32_Process -Filter "ProcessId = $BackendProcessId" -ErrorAction SilentlyContinue
    if ($null -ne $running) {
        $expectedMarker = "-Dtechstore.phase20=$DatabaseName"
        if ($running.Name -notin @('java.exe', 'javaw.exe') -or
            [string]$running.CommandLine -notlike "*$expectedMarker*" -or
            [string]$running.CommandLine -notlike '*techstore-backend-1.0.0*.jar*') {
            throw 'El PID no corresponde al backend aislado esperado; no se reinicio nada.'
        }
        Stop-Process -Id $BackendProcessId
        Wait-Process -Id $BackendProcessId -Timeout 20 -ErrorAction SilentlyContinue
    }
}

$portOwner = Get-NetTCPConnection -LocalPort 8092 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -ne $portOwner) {
    throw "El puerto aislado 8092 ya esta ocupado por el PID $($portOwner.OwningProcess)."
}

if ($Action -eq 'Start' -and [string]::IsNullOrWhiteSpace($DatabaseName)) {
    $DatabaseName = 'techstore_phase20_' + (Get-Date).ToUniversalTime().ToString('yyyyMMddHHmmss')
}
if (-not (Test-DatabaseName $DatabaseName)) {
    throw 'El nombre de la base temporal no cumple el formato seguro esperado.'
}
$exists = & $psql -h localhost -p 5432 -U techstore_user -d postgres -tAc `
    "SELECT 1 FROM pg_database WHERE datname='$DatabaseName'"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar PostgreSQL.' }
if ($Action -eq 'Start') {
    if ([string]$exists -match '1') {
        throw "La base temporal $DatabaseName ya existe y no se reutilizara."
    }
    Invoke-Psql 'postgres' "CREATE DATABASE $DatabaseName;"
} elseif ([string]$exists -notmatch '1') {
    throw "No existe la base temporal $DatabaseName que se intento reiniciar."
}

$uploads = Join-Path $phaseRoot "$DatabaseName\uploads"
New-Item -ItemType Directory -Force -Path $uploads | Out-Null
$logSuffix = if ($Action -eq 'Restart') { '.restart-' + (Get-Date).ToUniversalTime().ToString('yyyyMMddHHmmss') } else { '' }
$outLog = Join-Path $phaseRoot "$DatabaseName$logSuffix.out.log"
$errLog = Join-Path $phaseRoot "$DatabaseName$logSuffix.err.log"

$env:SERVER_PORT = '8092'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/$DatabaseName"
$env:SPRING_DATASOURCE_USERNAME = 'techstore_app'
$env:SPRING_DATASOURCE_PASSWORD = $runtimePassword
$env:FLYWAY_DB_URL = "jdbc:postgresql://localhost:5432/$DatabaseName"
$env:FLYWAY_DB_USERNAME = 'techstore_user'
$env:FLYWAY_DB_PASSWORD = $adminPassword
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5174,http://127.0.0.1:5174'
$env:APP_FRONTEND_URL = 'http://localhost:5174'
$env:APP_UPLOADS_DIR = $uploads
$env:PUSH_PROVIDER = 'none'
$env:SPRINGDOC_ENABLED = 'true'
$env:SPRING_MAIL_HOST = '127.0.0.1'
$env:SPRING_MAIL_PORT = '1'
$env:SPRING_MAIL_USERNAME = ''
$env:SPRING_MAIL_PASSWORD = ''
$env:SYNC_OUTBOX_ENABLED = 'true'

$jarArgument = '"' + $backendJar + '"'
$arguments = @("-Dtechstore.phase20=$DatabaseName", '-jar', $jarArgument)
$process = Start-Process -FilePath $java -ArgumentList $arguments `
    -WorkingDirectory (Join-Path $root 'techstore-backend') `
    -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog

$ready = $false
for ($attempt = 0; $attempt -lt 50; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) { break }
    try {
        $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8092/actuator/health' -TimeoutSec 2
        if ($health.status -eq 'UP') {
            $ready = $true
            break
        }
    } catch { }
}

if (-not $ready) {
    Write-Output "PHASE20_DATABASE=$DatabaseName"
    Write-Output "PHASE20_PID=$($process.Id)"
    Write-Output 'PHASE20_READY=false'
    Get-Content -LiteralPath $errLog -Tail 50 -ErrorAction SilentlyContinue
    Get-Content -LiteralPath $outLog -Tail 80 -ErrorAction SilentlyContinue
    throw 'El backend aislado no inicio correctamente.'
}

# El rol runtime se aprovisiona por base de datos. Flyway acaba de crear las
# tablas de esta base descartable, por lo que ahora se conceden solo los
# permisos de datos que usa la aplicacion (sin privilegios administrativos).
Invoke-Psql $DatabaseName "GRANT CONNECT ON DATABASE $DatabaseName TO techstore_app;"
Invoke-Psql $DatabaseName 'GRANT USAGE ON SCHEMA public TO techstore_app;'
Invoke-Psql $DatabaseName 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO techstore_app;'
Invoke-Psql $DatabaseName 'GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO techstore_app;'
Invoke-Psql $DatabaseName 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO techstore_app;'
Invoke-Psql $DatabaseName 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO techstore_app;'

Write-Output "PHASE20_DATABASE=$DatabaseName"
Write-Output "PHASE20_PID=$($process.Id)"
Write-Output 'PHASE20_BASE_URL=http://127.0.0.1:8092'
Write-Output 'PHASE20_READY=true'
