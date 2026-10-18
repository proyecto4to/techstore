param(
    [Parameter(Mandatory = $true)]
    [string]$ApiBaseUrl,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$VersionName,
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2100000000)]
    [int]$VersionCode,
    # Qué contarle a quien recibe la actualización dentro de la app: cada texto
    # es una línea de la pantalla de novedades. Sin esto la lista sale vacía, y
    # el changelog viejo se borra para que nunca describa otra versión.
    [string[]]$Novedades = @(),
    # Compilar a sabiendas sin notificaciones push. Sin esto, la falta de
    # claves de Firebase corta la compilación en lugar de publicar una versión
    # muda sin que nadie se entere.
    [switch]$SinPush,
    [switch]$OmitirPruebas
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'firebase-mobile-defines.ps1')
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

    $firebase = Get-FirebaseMobileDefines -Obligatorio:(-not $SinPush)
    $common = @(
        '--release', '--flavor', 'production', '-t', 'lib/main_production.dart',
        '--build-name', $VersionName, '--build-number', $VersionCode,
        "--dart-define=API_BASE_URL=$ApiBaseUrl",
        '--obfuscate', "--split-debug-info=$symbolsDir"
    ) + $firebase
    flutter build appbundle @common
    if ($LASTEXITCODE -ne 0) { throw 'La compilación AAB falló.' }
    # El APK se instala a mano en teléfonos, que son todos ARM: llevar x86_64
    # adentro sumaba 22 MB que nadie ejecuta. El AAB sí conserva todas las
    # arquitecturas, porque Play arma la descarga para cada equipo.
    flutter build apk @common --target-platform android-arm,android-arm64
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
    # Un APK compilado con cambios sin commitear no se puede volver a armar
    # desde ese commit. Queda anotado para que la metadata no prometa de más.
    $arbolLimpio = [string]::IsNullOrWhiteSpace((& git -C $root status --porcelain))
    $metadata = [ordered]@{
        versionName = $VersionName
        versionCode = $VersionCode
        buildDateUtc = [DateTime]::UtcNow.ToString('o')
        gitCommit = $commit
        gitTreeClean = $arbolLimpio
        environment = 'production'
        apiBaseUrl = $ApiBaseUrl
        applicationId = 'py.com.techstore.app'
        firebase = ($firebase.Count -gt 0)
        apk = $apkFinal
        apkSha256 = (Get-FileHash -LiteralPath $apkFinal -Algorithm SHA256).Hash.ToLowerInvariant()
        aab = $aabFinal
        aabSha256 = (Get-FileHash -LiteralPath $aabFinal -Algorithm SHA256).Hash.ToLowerInvariant()
        symbols = $symbolsDir
    }
    # El changelog se escribe junto a la metadata, nunca antes: si la versión
    # cambia y las novedades no, la app le muestra a la gente los cambios de
    # otra versión. Sin -Novedades se borra el anterior en lugar de dejarlo.
    $changelog = Join-Path $releaseDir 'changelog.json'
    if ($Novedades.Count -gt 0) {
        [ordered]@{
            versionName = $VersionName
            versionCode = $VersionCode
            cambios = @($Novedades)
        } | ConvertTo-Json | Set-Content -LiteralPath $changelog -Encoding UTF8
    }
    elseif (Test-Path -LiteralPath $changelog) {
        Remove-Item -LiteralPath $changelog -Force
        Write-Warning 'Sin -Novedades: se borró el changelog anterior y la actualización se ofrece sin lista de cambios.'
    }

    $metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $releaseDir 'release-metadata.json') -Encoding UTF8
    $metadata | Format-List
} finally {
    Pop-Location
}
