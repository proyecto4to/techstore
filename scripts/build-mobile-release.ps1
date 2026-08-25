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
$root = Split-Path -Parent $PSScriptRoot
$mobile = Join-Path $root 'techstore-mobile'
$keyProperties = Join-Path $mobile 'android\key.properties'
$releaseDir = Join-Path $root 'artifacts\mobile\release'
$symbolsDir = Join-Path $root "artifacts\mobile\symbols\$VersionName+$VersionCode"

$uri = $null
if (-not [Uri]::TryCreate($ApiBaseUrl, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne 'https') {
    throw 'ApiBaseUrl debe ser una URL HTTPS absoluta.'
}
if ($uri.Host -match 'localhost|127\.0\.0\.1|10\.0\.2\.2|example|dominio-propio|\.tld$') {
    throw 'ApiBaseUrl contiene un host local o placeholder.'
}
if (-not (Test-Path -LiteralPath $keyProperties)) {
    throw 'Falta techstore-mobile/android/key.properties. Use key.properties.example sin versionar secretos.'
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

    $common = @(
        '--release', '--flavor', 'production', '-t', 'lib/main_production.dart',
        '--build-name', $VersionName, '--build-number', $VersionCode,
        "--dart-define=API_BASE_URL=$ApiBaseUrl",
        '--obfuscate', "--split-debug-info=$symbolsDir"
    )
    flutter build appbundle @common
    if ($LASTEXITCODE -ne 0) { throw 'La compilación AAB falló.' }
    flutter build apk @common
    if ($LASTEXITCODE -ne 0) { throw 'La compilación APK falló.' }

    New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
    $aab = Join-Path $mobile 'build\app\outputs\bundle\productionRelease\app-production-release.aab'
    $apk = Join-Path $mobile 'build\app\outputs\flutter-apk\app-production-release.apk'
    $aabFinal = Join-Path $releaseDir "TechStore-$VersionName+$VersionCode.aab"
    $apkFinal = Join-Path $releaseDir "TechStore-$VersionName+$VersionCode.apk"
    Copy-Item -LiteralPath $aab -Destination $aabFinal -Force
    Copy-Item -LiteralPath $apk -Destination $apkFinal -Force

    node (Join-Path $root 'scripts\ci-security-check.mjs') --archive $apkFinal
    if ($LASTEXITCODE -ne 0) { throw 'El escaneo de seguridad del APK falló.' }

    $commit = (& git -C $root rev-parse HEAD).Trim()
    $metadata = [ordered]@{
        versionName = $VersionName
        versionCode = $VersionCode
        buildDateUtc = [DateTime]::UtcNow.ToString('o')
        gitCommit = $commit
        environment = 'production'
        apiBaseUrl = $ApiBaseUrl
        applicationId = 'py.com.techstore.app'
        apk = $apkFinal
        apkSha256 = (Get-FileHash -LiteralPath $apkFinal -Algorithm SHA256).Hash.ToLowerInvariant()
        aab = $aabFinal
        aabSha256 = (Get-FileHash -LiteralPath $aabFinal -Algorithm SHA256).Hash.ToLowerInvariant()
        symbols = $symbolsDir
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $releaseDir 'release-metadata.json') -Encoding UTF8
    $metadata | Format-List
} finally {
    Pop-Location
}
