[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApiBaseUrl,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$VersionName,
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2100000000)]
    [int]$VersionCode,
    [switch]$OmitirPruebas
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'firebase-mobile-defines.ps1')
$root = Split-Path -Parent $PSScriptRoot
$mobile = Join-Path $root 'techstore-mobile'
$keyProperties = Join-Path $mobile 'android\key.properties'
$releaseDir = Join-Path $root 'artifacts\mobile\private'
$symbolsDir = Join-Path $root "artifacts\mobile\symbols-private\$VersionName+$VersionCode"

function Test-PrivateIp([string]$hostName) {
    $address = $null
    if (-not [Net.IPAddress]::TryParse($hostName, [ref]$address) -or $address.AddressFamily -ne 'InterNetwork') {
        return $false
    }
    $bytes = $address.GetAddressBytes()
    return $bytes[0] -eq 10 -or
        ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or
        ($bytes[0] -eq 192 -and $bytes[1] -eq 168)
}

$uri = $null
if (-not [Uri]::TryCreate($ApiBaseUrl, [UriKind]::Absolute, [ref]$uri) -or
    $uri.Scheme -notin @('http', 'https')) {
    throw 'ApiBaseUrl debe ser una URL HTTP/HTTPS absoluta.'
}
if ($uri.Host -match 'example|dominio-propio|\.tld$') {
    throw 'ApiBaseUrl contiene un placeholder.'
}
if ($uri.Scheme -eq 'http' -and -not (Test-PrivateIp $uri.Host)) {
    throw 'HTTP solo se permite hacia una IPv4 privada RFC1918; para otros hosts use HTTPS.'
}
if (-not (Test-Path -LiteralPath $keyProperties)) {
    throw 'Falta android/key.properties. Ejecute scripts/provision-mobile-private-signing.ps1.'
}

Push-Location $mobile
try {
    flutter pub get
    if ($LASTEXITCODE -ne 0) { throw 'flutter pub get falló.' }
    if (-not $OmitirPruebas) {
        flutter analyze --fatal-infos
        if ($LASTEXITCODE -ne 0) { throw 'flutter analyze falló.' }
        flutter test
        if ($LASTEXITCODE -ne 0) { throw 'flutter test falló.' }
    }

    # En el APK privado la falta de Firebase solo avisa: es para probar en un
    # teléfono conocido y no siempre hace falta el push.
    $firebase = Get-FirebaseMobileDefines
    flutter build apk `
        --release `
        --flavor private `
        -t lib/main_private.dart `
        --build-name $VersionName `
        --build-number $VersionCode `
        "--dart-define=API_BASE_URL=$ApiBaseUrl" `
        --obfuscate `
        "--split-debug-info=$symbolsDir" `
        @firebase
    if ($LASTEXITCODE -ne 0) { throw 'La compilación APK privada falló.' }

    $sourceApk = Join-Path $mobile 'build\app\outputs\flutter-apk\app-private-release.apk'
    if (-not (Test-Path -LiteralPath $sourceApk)) { throw 'Flutter no produjo app-private-release.apk.' }

    node (Join-Path $root 'scripts\ci-security-check.mjs') --archive $sourceApk
    if ($LASTEXITCODE -ne 0) { throw 'El escaneo de seguridad del APK falló.' }

    $sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Android\Sdk' }
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    $apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
    if (-not (Test-Path -LiteralPath $apksigner)) { throw 'No se encontró apksigner.' }
    $signature = @(& $apksigner verify --verbose --print-certs $sourceApk 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'La firma del APK no es válida.' }
    $certificateSha256 = ($signature | Select-String 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)' |
        Select-Object -First 1).Matches.Groups[1].Value.ToLowerInvariant()
    if (-not $certificateSha256) { throw 'No se pudo extraer el SHA-256 del certificado.' }
    if (($signature -join "`n") -match 'Android Debug') { throw 'El APK privado quedó firmado con certificado debug.' }

    New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
    $finalApk = Join-Path $releaseDir "TechStore-Private-$VersionName+$VersionCode.apk"
    Copy-Item -LiteralPath $sourceApk -Destination $finalApk -Force

    $gitStatus = @(& git -C $root status --porcelain)
    $metadata = [ordered]@{
        versionName = $VersionName
        versionCode = $VersionCode
        buildDateUtc = [DateTime]::UtcNow.ToString('o')
        gitCommit = (& git -C $root rev-parse HEAD).Trim()
        gitDirty = $gitStatus.Count -gt 0
        environment = 'private'
        distribution = 'direct-apk'
        apiBaseUrl = $ApiBaseUrl
        applicationId = 'py.com.techstore.app.private'
        apk = $finalApk
        apkSha256 = (Get-FileHash -LiteralPath $finalApk -Algorithm SHA256).Hash.ToLowerInvariant()
        signingCertificateSha256 = $certificateSha256
        symbols = $symbolsDir
    }
    $metadataPath = Join-Path $releaseDir 'private-release-metadata.json'
    [IO.File]::WriteAllText(
        $metadataPath,
        ($metadata | ConvertTo-Json) + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false)
    )
    $metadata | Format-List
} finally {
    Pop-Location
}
