# Restaura un respaldo verificado de TechStore (base de datos + uploads).
# No se ejecuta sin una confirmacion explicita y crea un respaldo de seguridad
# del estado actual antes de reemplazar datos.
param(
    [string]$BackupPath,
    [switch]$NoPause
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$techStoreLocal = Join-Path $env:LOCALAPPDATA 'TechStore'
$carpeta = Join-Path $techStoreLocal 'backups'
$pgData = Join-Path $techStoreLocal 'pgdata'
$pgLog = Join-Path $techStoreLocal 'pg.log'
$pgRestore = Join-Path $root 'tools\pgsql\bin\pg_restore.exe'
$pgCtl = Join-Path $root 'tools\pgsql\bin\pg_ctl.exe'
$pgIsReady = Join-Path $root 'tools\pgsql\bin\pg_isready.exe'
$secretsLocal = Join-Path $root 'secrets.local.ps1'
$temporalUploads = $null
$respaldoSeguridad = $null
$passwordAnterior = $env:PGPASSWORD

function Pausar-SiCorresponde {
    if (-not $NoPause) { Read-Host '  Enter para salir' | Out-Null }
}

function Fallar($mensaje) {
    throw $mensaje
}

function Es-RutaHoja($nombre) {
    if ([string]::IsNullOrWhiteSpace($nombre)) { return $false }
    return ([IO.Path]::GetFileName($nombre) -eq $nombre)
}

function Get-PidPuerto([int]$puerto) {
    $conexion = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue |
        Sort-Object OwningProcess | Select-Object -First 1
    if ($null -eq $conexion) { return $null }
    return [int]$conexion.OwningProcess
}

function Verificar-ZipYExtraer($rutaZip) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $destino = Join-Path $techStoreLocal ("restore-uploads-{0}" -f [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $destino | Out-Null
    $raizDestino = [IO.Path]::GetFullPath($destino + [IO.Path]::DirectorySeparatorChar)

    $archivo = [IO.Compression.ZipFile]::OpenRead($rutaZip)
    try {
        foreach ($entrada in $archivo.Entries) {
            $rutaEntrada = $entrada.FullName.Replace('/', [IO.Path]::DirectorySeparatorChar)
            $rutaFinal = [IO.Path]::GetFullPath((Join-Path $destino $rutaEntrada))
            if (-not $rutaFinal.StartsWith($raizDestino, [StringComparison]::OrdinalIgnoreCase)) {
                Fallar "el ZIP contiene una ruta insegura: $($entrada.FullName)"
            }

            # Un backup de uploads solo debe contener archivos/directorios normales.
            $modoUnix = (($entrada.ExternalAttributes -shr 16) -band 0xF000)
            if ($modoUnix -eq 0xA000) {
                Fallar "el ZIP contiene un enlace simbolico no permitido: $($entrada.FullName)"
            }
        }
    } finally {
        $archivo.Dispose()
    }

    [IO.Compression.ZipFile]::ExtractToDirectory($rutaZip, $destino)
    return $destino
}

Write-Host ''
Write-Host '  ============================' -ForegroundColor DarkYellow
Write-Host '    TechStore - Restaurar' -ForegroundColor DarkYellow
Write-Host '  ============================' -ForegroundColor DarkYellow
Write-Host ''

try {
    foreach ($requerido in @($pgRestore, $pgCtl, $pgIsReady, $secretsLocal)) {
        if (-not (Test-Path -LiteralPath $requerido)) { Fallar "no se encontro $requerido" }
    }
    . $secretsLocal

    $dbPassword = $env:SPRING_DATASOURCE_PASSWORD
    $dbUser = if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { 'techstore_user' }
    $dbHost = if ($env:TECHSTORE_DB_HOST) { $env:TECHSTORE_DB_HOST } else { 'localhost' }
    $dbPort = if ($env:TECHSTORE_DB_PORT) { $env:TECHSTORE_DB_PORT } else { '5432' }
    $dbName = if ($env:TECHSTORE_DB_NAME) { $env:TECHSTORE_DB_NAME } else { 'techstore_db' }
    if ([string]::IsNullOrWhiteSpace($dbPassword) -or $dbPassword.Length -lt 16 -or $dbPassword -eq 'techstore_pass_dev') {
        Fallar 'la credencial administrativa de PostgreSQL falta o es insegura'
    }

    if ([string]::IsNullOrWhiteSpace($BackupPath)) {
        $backups = @(Get-ChildItem -LiteralPath $carpeta -Filter 'techstore-*.dump' -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^techstore-\d{8}-\d{6}(?:\d{3})?\.dump$' } |
            Sort-Object LastWriteTime -Descending)
        if ($backups.Count -eq 0) { Fallar "no hay respaldos disponibles en $carpeta" }

        Write-Host '  Respaldos disponibles (el mas reciente primero):' -ForegroundColor Cyan
        for ($i = 0; $i -lt $backups.Count; $i++) {
            $b = $backups[$i]
            $tieneManifest = Test-Path -LiteralPath (Join-Path $carpeta (([IO.Path]::GetFileNameWithoutExtension($b.Name)) + '.manifest.json'))
            $tipo = if ($tieneManifest) { 'completo/verificado' } else { 'antiguo: solo BD' }
            Write-Host ("    [{0}] {1}   ({2} KB, {3})" -f $i, $b.LastWriteTime.ToString('dd/MM/yyyy HH:mm:ss'), [math]::Round($b.Length / 1KB), $tipo)
        }
        Write-Host ''

        $sel = Read-Host '  Numero del respaldo (Enter = mas reciente, X = cancelar)'
        if ($sel -in @('X', 'x')) { Write-Host '  Cancelado.'; exit 0 }
        if ([string]::IsNullOrWhiteSpace($sel)) { $sel = '0' }
        $indice = 0
        if (-not [int]::TryParse($sel, [ref]$indice) -or $indice -lt 0 -or $indice -ge $backups.Count) {
            Fallar 'seleccion invalida'
        }
        $elegido = $backups[$indice]
    } else {
        $rutaSolicitada = [IO.Path]::GetFullPath($BackupPath)
        if (-not (Test-Path -LiteralPath $rutaSolicitada -PathType Leaf)) { Fallar "no existe $rutaSolicitada" }
        if ([IO.Path]::GetExtension($rutaSolicitada) -ne '.dump') { Fallar 'el respaldo debe ser un archivo .dump' }
        $elegido = Get-Item -LiteralPath $rutaSolicitada
    }

    Write-Host '  Validando el dump...' -NoNewline
    & $pgRestore --list $elegido.FullName *> $null
    if ($LASTEXITCODE -ne 0) { Fallar 'pg_restore no reconoce el dump seleccionado' }
    Write-Host ' correcto' -ForegroundColor Green

    $baseElegida = [IO.Path]::GetFileNameWithoutExtension($elegido.Name)
    $manifestPath = Join-Path $elegido.DirectoryName "$baseElegida.manifest.json"
    $esCompleto = Test-Path -LiteralPath $manifestPath -PathType Leaf
    $uploadsZip = $null

    if ($esCompleto) {
        $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        if ([int]$manifest.version -ne 1) { Fallar 'version de manifest no soportada' }
        if (-not (Es-RutaHoja ([string]$manifest.dump.file)) -or $manifest.dump.file -ne $elegido.Name) {
            Fallar 'el manifest no corresponde al dump seleccionado'
        }
        if (-not (Es-RutaHoja ([string]$manifest.uploads.file))) { Fallar 'nombre de uploads inseguro en el manifest' }

        $hashDump = (Get-FileHash -Algorithm SHA256 -LiteralPath $elegido.FullName).Hash
        if ($hashDump -ne [string]$manifest.dump.sha256) { Fallar 'el hash del dump no coincide; el respaldo puede estar danado' }

        $uploadsZip = Join-Path $elegido.DirectoryName ([string]$manifest.uploads.file)
        if (-not (Test-Path -LiteralPath $uploadsZip -PathType Leaf)) { Fallar "falta $uploadsZip" }
        $hashUploads = (Get-FileHash -Algorithm SHA256 -LiteralPath $uploadsZip).Hash
        if ($hashUploads -ne [string]$manifest.uploads.sha256) { Fallar 'el hash de uploads no coincide; el respaldo puede estar danado' }

        Write-Host '  Validando y preparando uploads...' -NoNewline
        $temporalUploads = Verificar-ZipYExtraer $uploadsZip
        Write-Host ' correctos' -ForegroundColor Green
    } else {
        Write-Host '  AVISO: respaldo antiguo sin manifest; solo se restaurara la base.' -ForegroundColor Yellow
        Write-Host '         Los uploads actuales no se modificaran.' -ForegroundColor Yellow
    }

    Write-Host ''
    Write-Host '  Se reemplazaran los datos actuales por el respaldo del' -ForegroundColor Yellow
    Write-Host ("  {0}. Antes se creara un respaldo de seguridad." -f $elegido.LastWriteTime.ToString('dd/MM/yyyy HH:mm:ss')) -ForegroundColor Yellow
    $fraseEsperada = if ($esCompleto) { 'RESTAURAR' } else { 'RESTAURAR-SOLO-BD' }
    $confirma = Read-Host "  Escribi $fraseEsperada para continuar"
    if ($confirma -cne $fraseEsperada) { Write-Host '  Cancelado.'; exit 0 }

    # Solo se inicia/administra el cluster portable propiedad de TechStore.
    & $pgCtl -D $pgData status *> $null
    if ($LASTEXITCODE -ne 0) {
        $ocupantePg = Get-PidPuerto 5432
        if ($null -ne $ocupantePg) { Fallar "el puerto 5432 pertenece al PID $ocupantePg; no se tocara ese proceso" }
        Write-Host '  Iniciando PostgreSQL...'
        Start-Process -FilePath $pgCtl -ArgumentList "-D `"$pgData`" -l `"$pgLog`" start" -WindowStyle Hidden
    }
    $baseLista = $false
    for ($i = 0; $i -lt 30; $i++) {
        & $pgIsReady -h $dbHost -p $dbPort -d $dbName *> $null
        if ($LASTEXITCODE -eq 0) { $baseLista = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $baseLista) { Fallar 'PostgreSQL no acepta conexiones despues de 60 segundos' }

    Write-Host '  Creando respaldo de seguridad del estado actual...' -NoNewline
    # Durante una restauracion no se rota: el respaldo elegido podria ser uno
    # antiguo y debe seguir existiendo hasta que termine todo el proceso.
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'backup-techstore.ps1') -Silencioso -MaxBackups 500
    if ($LASTEXITCODE -ne 0) { Fallar 'el respaldo previo fallo; no se modifico la instalacion' }
    $respaldoSeguridad = Get-ChildItem -LiteralPath $carpeta -Filter 'techstore-*.manifest.json' -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    Write-Host ' listo' -ForegroundColor Green

    # El script de parada solo detiene PIDs registrados por el lanzador.
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'detener-techstore.ps1') -SinBackup -MantenerPostgres -NoEspera
    if ($LASTEXITCODE -ne 0) { Fallar 'no se pudo preparar la detencion segura de TechStore' }
    $ocupanteBackend = Get-PidPuerto 8090
    if ($null -ne $ocupanteBackend) {
        Fallar "el puerto 8090 sigue ocupado por el PID $ocupanteBackend; cerralo manualmente y reintenta"
    }

    Write-Host '  Restaurando la base en una sola transaccion...' -NoNewline
    $env:PGPASSWORD = $dbPassword
    & $pgRestore -h $dbHost -p $dbPort -U $dbUser -d $dbName --clean --if-exists --no-owner --single-transaction $elegido.FullName 2>&1 |
        Where-Object { $_ -notmatch 'does not exist, skipping' }
    $codigoRestore = $LASTEXITCODE
    if ($codigoRestore -ne 0) { Fallar "pg_restore devolvio el codigo $codigoRestore; la transaccion fue revertida" }
    Write-Host ' lista' -ForegroundColor Green

    Write-Host '  Reaplicando el rol limitado de la aplicacion...' -NoNewline
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'configurar-rol-app.ps1') *> $null
    if ($LASTEXITCODE -ne 0) { Fallar 'la base fue restaurada, pero no se pudo reconfigurar techstore_app' }
    Write-Host ' listo' -ForegroundColor Green

    if ($esCompleto) {
        $uploadsDir = if ($env:APP_UPLOADS_DIR) { $env:APP_UPLOADS_DIR } else { Join-Path $techStoreLocal 'uploads' }
        $uploadsDir = [IO.Path]::GetFullPath($uploadsDir)
        $raizPermitida = [IO.Path]::GetFullPath($techStoreLocal + [IO.Path]::DirectorySeparatorChar)
        if (-not $uploadsDir.StartsWith($raizPermitida, [StringComparison]::OrdinalIgnoreCase)) {
            Fallar "APP_UPLOADS_DIR queda fuera de $techStoreLocal; no se reemplazaran archivos"
        }

        $uploadsAnterior = Join-Path $techStoreLocal ("uploads-before-restore-{0}" -f [guid]::NewGuid().ToString('N'))
        $habiaUploads = Test-Path -LiteralPath $uploadsDir
        if ($habiaUploads) { Move-Item -LiteralPath $uploadsDir -Destination $uploadsAnterior }
        try {
            Move-Item -LiteralPath $temporalUploads -Destination $uploadsDir
            $temporalUploads = $null
        } catch {
            if (Test-Path -LiteralPath $uploadsDir) { Remove-Item -LiteralPath $uploadsDir -Recurse -Force }
            if ($habiaUploads -and (Test-Path -LiteralPath $uploadsAnterior)) {
                Move-Item -LiteralPath $uploadsAnterior -Destination $uploadsDir
            }
            throw
        }
        if (Test-Path -LiteralPath $uploadsAnterior) { Remove-Item -LiteralPath $uploadsAnterior -Recurse -Force }
        Write-Host '  Uploads restaurados.' -ForegroundColor Green
    }

    Write-Host ''
    Write-Host '  Restauracion completada y verificada.' -ForegroundColor Green
    if ($null -ne $respaldoSeguridad) {
        Write-Host "  Respaldo previo conservado: $($respaldoSeguridad.FullName)" -ForegroundColor Cyan
    }
    Write-Host '  Inicia TechStore nuevamente desde el acceso directo.' -ForegroundColor Cyan
    Pausar-SiCorresponde
    exit 0
} catch {
    Write-Host ''
    Write-Host "  ERROR: $($_.Exception.Message)" -ForegroundColor Red
    if ($null -ne $respaldoSeguridad) {
        Write-Host "  El respaldo previo se conserva en: $($respaldoSeguridad.FullName)" -ForegroundColor Yellow
    }
    Pausar-SiCorresponde
    exit 1
} finally {
    if ($null -eq $passwordAnterior) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
    else { $env:PGPASSWORD = $passwordAnterior }
    if ($null -ne $temporalUploads -and (Test-Path -LiteralPath $temporalUploads)) {
        $rutaTemporal = [IO.Path]::GetFullPath($temporalUploads)
        $raizPermitida = [IO.Path]::GetFullPath($techStoreLocal + [IO.Path]::DirectorySeparatorChar)
        if ($rutaTemporal.StartsWith($raizPermitida, [StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $rutaTemporal -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}
