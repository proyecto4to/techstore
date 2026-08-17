# Respalda la base y los uploads de la instalacion local/portable de TechStore.
param(
    [switch]$Silencioso,
    [ValidateRange(1, 500)]
    [int]$MaxBackups = 20
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$secretsLocal = Join-Path $root 'secrets.local.ps1'

function Escribir($texto, $color = 'Gray') {
    if (-not $Silencioso) { Write-Host $texto -ForegroundColor $color }
}

if (-not (Test-Path -LiteralPath $secretsLocal)) {
    Escribir '  ERROR: falta secrets.local.ps1; no se usaran credenciales por defecto.' 'Red'
    exit 1
}
. $secretsLocal

$pgDump = Join-Path $root 'tools\pgsql\bin\pg_dump.exe'
$pgRestore = Join-Path $root 'tools\pgsql\bin\pg_restore.exe'
foreach ($herramienta in @($pgDump, $pgRestore)) {
    if (-not (Test-Path -LiteralPath $herramienta)) {
        Escribir "  ERROR: no se encontro $herramienta" 'Red'
        exit 1
    }
}

$dbPassword = if ($env:FLYWAY_DB_PASSWORD) { $env:FLYWAY_DB_PASSWORD } else { $env:SPRING_DATASOURCE_PASSWORD }
$dbUser = if ($env:FLYWAY_DB_USERNAME) { $env:FLYWAY_DB_USERNAME } elseif ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { 'techstore_user' }
$dbHost = if ($env:TECHSTORE_DB_HOST) { $env:TECHSTORE_DB_HOST } else { 'localhost' }
$dbPort = if ($env:TECHSTORE_DB_PORT) { $env:TECHSTORE_DB_PORT } else { '5432' }
$dbName = if ($env:TECHSTORE_DB_NAME) { $env:TECHSTORE_DB_NAME } else { 'techstore_db' }
if ([string]::IsNullOrWhiteSpace($dbPassword) -or $dbPassword.Length -lt 16 -or $dbPassword -eq 'techstore_pass_dev') {
    Escribir '  ERROR: la contraseña administrativa de la base falta o sigue siendo insegura.' 'Red'
    exit 1
}

$techStoreLocal = Join-Path $env:LOCALAPPDATA 'TechStore'
$carpeta = Join-Path $techStoreLocal 'backups'
$uploadsDir = if ($env:APP_UPLOADS_DIR) { $env:APP_UPLOADS_DIR } else { Join-Path $techStoreLocal 'uploads' }
New-Item -ItemType Directory -Force -Path $carpeta | Out-Null
New-Item -ItemType Directory -Force -Path $uploadsDir | Out-Null

$sello = Get-Date -Format 'yyyyMMdd-HHmmssfff'
$base = "techstore-$sello"
$destinoDump = Join-Path $carpeta "$base.dump"
$destinoZip = Join-Path $carpeta "$base.uploads.zip"
$destinoManifest = Join-Path $carpeta "$base.manifest.json"
$temporalDump = Join-Path $carpeta "$base.partial.dump"
$temporalZip = Join-Path $carpeta "$base.partial.zip"
$temporalManifest = Join-Path $carpeta "$base.partial.manifest.json"
$temporales = @($temporalDump, $temporalZip, $temporalManifest)

$passwordAnterior = $env:PGPASSWORD
$fallo = $null
try {
    Escribir '  Respaldando base de datos...' 'DarkYellow'
    $env:PGPASSWORD = $dbPassword
    & $pgDump -h $dbHost -p $dbPort -U $dbUser -Fc -f $temporalDump $dbName
    $codigoDump = $LASTEXITCODE
    if ($codigoDump -ne 0) { throw "pg_dump devolvio $codigoDump" }

    $null = & $pgRestore --list $temporalDump 2>&1
    $codigoValidacion = $LASTEXITCODE
    if ($codigoValidacion -ne 0) { throw "pg_restore no pudo validar el dump (codigo $codigoValidacion)" }

    Escribir '  Respaldando archivos subidos...' 'DarkYellow'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::CreateFromDirectory(
        (Resolve-Path -LiteralPath $uploadsDir).Path,
        $temporalZip,
        [System.IO.Compression.CompressionLevel]::Optimal,
        $false
    )
    $zipPrueba = [System.IO.Compression.ZipFile]::OpenRead($temporalZip)
    $zipPrueba.Dispose()

    $manifest = [ordered]@{
        version = 1
        createdAtUtc = (Get-Date).ToUniversalTime().ToString('o')
        database = $dbName
        dump = [ordered]@{
            file = [IO.Path]::GetFileName($destinoDump)
            sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $temporalDump).Hash
        }
        uploads = [ordered]@{
            file = [IO.Path]::GetFileName($destinoZip)
            sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $temporalZip).Hash
        }
    }
    $manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $temporalManifest -Encoding UTF8

    Move-Item -LiteralPath $temporalDump -Destination $destinoDump
    Move-Item -LiteralPath $temporalZip -Destination $destinoZip
    Move-Item -LiteralPath $temporalManifest -Destination $destinoManifest
} catch {
    $fallo = $_
} finally {
    if ($null -eq $passwordAnterior) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
    else { $env:PGPASSWORD = $passwordAnterior }
    foreach ($temporal in $temporales) {
        if (Test-Path -LiteralPath $temporal) { Remove-Item -LiteralPath $temporal -Force -ErrorAction SilentlyContinue }
    }
}

if ($null -ne $fallo) {
    Escribir "  ERROR: no se pudo crear un respaldo verificado ($fallo)." 'Red'
    exit 1
}

$kbDump = [math]::Round((Get-Item -LiteralPath $destinoDump).Length / 1KB)
$kbZip = [math]::Round((Get-Item -LiteralPath $destinoZip).Length / 1KB)
Escribir "  Respaldo listo: $destinoDump (BD: $kbDump KB, uploads: $kbZip KB)" 'Green'

$viejos = @(Get-ChildItem -LiteralPath $carpeta -Filter 'techstore-*.dump' |
    Where-Object { $_.Name -match '^techstore-\d{8}-\d{6}(?:\d{3})?\.dump$' } |
    Sort-Object LastWriteTime -Descending | Select-Object -Skip $MaxBackups)
foreach ($dumpViejo in $viejos) {
    $baseVieja = [IO.Path]::GetFileNameWithoutExtension($dumpViejo.Name)
    foreach ($nombre in @("$baseVieja.dump", "$baseVieja.uploads.zip", "$baseVieja.manifest.json")) {
        $artefacto = Join-Path $carpeta $nombre
        if (Test-Path -LiteralPath $artefacto) { Remove-Item -LiteralPath $artefacto -Force }
    }
    Escribir "  Rotacion: eliminado respaldo viejo $baseVieja" 'DarkGray'
}

exit 0
