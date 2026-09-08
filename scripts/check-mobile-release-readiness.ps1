[CmdletBinding()]
param(
    [string]$ApiBaseUrl = $env:TECHSTORE_PRODUCTION_API_URL,
    [switch]$PlayTrackVerified,
    [switch]$AppLinksVerified,
    [switch]$FirebaseDeliveryVerified,
    [switch]$PhysicalSmokeVerified,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$mobile = Join-Path $root 'techstore-mobile'
$android = Join-Path $mobile 'android'
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][ValidateSet('PASS', 'BLOCKED')][string]$State,
        [Parameter(Mandatory = $true)][string]$Detail
    )
    $checks.Add([pscustomobject]@{ check = $Name; state = $State; detail = $Detail })
}

function Read-SimpleProperties {
    param([Parameter(Mandatory = $true)][string]$Path)
    $properties = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $properties[$key] = $value
    }
    return $properties
}

function Find-Keytool {
    $command = Get-Command keytool -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    foreach ($candidate in @(
        (Join-Path (Split-Path -Parent $PSScriptRoot) 'tools\jdk-21\bin\keytool.exe'),
        'C:\Program Files\Java\jdk-22\bin\keytool.exe',
        'C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot\bin\keytool.exe'
    )) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    return $null
}

$gradle = Join-Path $android 'app\build.gradle.kts'
if ((Test-Path -LiteralPath $gradle) -and
    (Get-Content -LiteralPath $gradle -Raw -Encoding utf8) -match 'applicationId\s*=\s*"py\.com\.techstore\.app"') {
    Add-Check 'application_id' 'PASS' 'py.com.techstore.app'
} else {
    Add-Check 'application_id' 'BLOCKED' 'No se confirmó py.com.techstore.app en Gradle.'
}

$releaseScript = Join-Path $root 'scripts\build-mobile-release.ps1'
if (Test-Path -LiteralPath $releaseScript) {
    Add-Check 'release_pipeline' 'PASS' 'Script de build final presente.'
} else {
    Add-Check 'release_pipeline' 'BLOCKED' 'Falta scripts/build-mobile-release.ps1.'
}

$apiUri = $null
$apiValid = $false
if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
    Add-Check 'production_api' 'BLOCKED' 'Falta TECHSTORE_PRODUCTION_API_URL o -ApiBaseUrl.'
} elseif (-not [Uri]::TryCreate($ApiBaseUrl, [UriKind]::Absolute, [ref]$apiUri) -or
    $apiUri.Scheme -ne 'https' -or
    $apiUri.Host -match 'localhost|127\.0\.0\.1|10\.0\.2\.2|example|dominio-propio|\.tld$') {
    Add-Check 'production_api' 'BLOCKED' 'La URL debe ser HTTPS, pública y no placeholder.'
} else {
    try {
        $addresses = [System.Net.Dns]::GetHostAddresses($apiUri.Host)
        if ($addresses.Count -eq 0) { throw 'DNS sin direcciones' }
        $port = if ($apiUri.IsDefaultPort) { 443 } else { $apiUri.Port }
        $client = [System.Net.Sockets.TcpClient]::new()
        try {
            $client.Connect($apiUri.Host, $port)
            $tls = [System.Net.Security.SslStream]::new($client.GetStream(), $false)
            try { $tls.AuthenticateAsClient($apiUri.Host) } finally { $tls.Dispose() }
        } finally {
            $client.Dispose()
        }
        $apiValid = $true
        Add-Check 'production_api' 'PASS' "DNS y TLS válidos para $($apiUri.Host)."
    } catch {
        Add-Check 'production_api' 'BLOCKED' "DNS/TLS no verificable para $($apiUri.Host): $($_.Exception.Message)"
    }
}

$keyPropertiesPath = Join-Path $android 'key.properties'
if (-not (Test-Path -LiteralPath $keyPropertiesPath)) {
    Add-Check 'production_signing' 'BLOCKED' 'Falta android/key.properties.'
} else {
    try {
        $properties = Read-SimpleProperties $keyPropertiesPath
        $required = @('storeFile', 'storePassword', 'keyAlias', 'keyPassword')
        $missing = @($required | Where-Object { -not $properties.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($properties[$_]) })
        if ($missing.Count -gt 0) { throw "Faltan propiedades: $($missing -join ', ')" }
        if (($required | Where-Object { $properties[$_] -match 'REEMPLAZAR|placeholder|ruta/externa' }).Count -gt 0) {
            throw 'La configuración contiene placeholders.'
        }

        $storeFile = $properties['storeFile']
        if (-not [IO.Path]::IsPathRooted($storeFile)) {
            $storeFile = [IO.Path]::GetFullPath((Join-Path (Join-Path $android 'app') $storeFile))
        }
        if (-not (Test-Path -LiteralPath $storeFile -PathType Leaf)) { throw 'El keystore referenciado no existe.' }

        $keytool = Find-Keytool
        if ([string]::IsNullOrWhiteSpace($keytool)) { throw 'No se encontró keytool.' }
        $passwordVariable = 'TECHSTORE_RELEASE_READINESS_PASSWORD'
        $previousPassword = [Environment]::GetEnvironmentVariable($passwordVariable, 'Process')
        try {
            [Environment]::SetEnvironmentVariable($passwordVariable, $properties['storePassword'], 'Process')
            & $keytool -list -keystore $storeFile '-storepass:env' $passwordVariable -alias $properties['keyAlias'] *> $null
            if ($LASTEXITCODE -ne 0) { throw 'keytool no pudo validar el alias configurado.' }
        } finally {
            [Environment]::SetEnvironmentVariable($passwordVariable, $previousPassword, 'Process')
        }
        Add-Check 'production_signing' 'PASS' 'Keystore y alias válidos; las contraseñas no se imprimieron.'
    } catch {
        Add-Check 'production_signing' 'BLOCKED' $_.Exception.Message
    }
}

$firebasePath = Join-Path $android 'app\google-services.json'
if (-not (Test-Path -LiteralPath $firebasePath)) {
    Add-Check 'firebase_config' 'BLOCKED' 'Falta android/app/google-services.json production.'
} else {
    try {
        $firebase = Get-Content -LiteralPath $firebasePath -Raw -Encoding utf8 | ConvertFrom-Json
        $packages = @($firebase.client | ForEach-Object { $_.client_info.android_client_info.package_name })
        if ($packages -notcontains 'py.com.techstore.app') { throw 'Firebase no contiene py.com.techstore.app.' }
        Add-Check 'firebase_config' 'PASS' 'Firebase contiene el package production.'
    } catch {
        Add-Check 'firebase_config' 'BLOCKED' $_.Exception.Message
    }
}

$adb = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $adb) {
    Add-Check 'physical_android' 'BLOCKED' 'ADB no está disponible.'
} else {
    $deviceLines = @(& $adb.Source devices -l 2>$null | Where-Object { $_ -match '^\S+\s+device\b' -and $_ -notmatch '^emulator-' })
    if ($deviceLines.Count -gt 0) {
        Add-Check 'physical_android' 'PASS' "$($deviceLines.Count) dispositivo(s) físico(s) autorizado(s)."
    } else {
        Add-Check 'physical_android' 'BLOCKED' 'ADB no detecta un Android físico autorizado.'
    }
}

$dirty = @(& git -C $root status --porcelain)
if ($LASTEXITCODE -ne 0) {
    Add-Check 'git_release_source' 'BLOCKED' 'No se pudo consultar Git.'
} elseif ($dirty.Count -gt 0) {
    Add-Check 'git_release_source' 'BLOCKED' 'El worktree tiene cambios; el release final debe corresponder a un commit exacto.'
} else {
    Add-Check 'git_release_source' 'PASS' (& git -C $root rev-parse HEAD).Trim()
}

Add-Check 'google_play_track' $(if ($PlayTrackVerified) { 'PASS' } else { 'BLOCKED' }) `
    $(if ($PlayTrackVerified) { 'Pista Play declarada verificada.' } else { 'Falta verificar Play App Signing y una pista controlada.' })
Add-Check 'app_links' $(if ($AppLinksVerified -and $apiValid) { 'PASS' } else { 'BLOCKED' }) `
    $(if ($AppLinksVerified -and $apiValid) { 'assetlinks.json y dominio declarados verificados.' } else { 'Falta verificar dominio HTTPS y assetlinks.json.' })
Add-Check 'firebase_delivery' $(if ($FirebaseDeliveryVerified) { 'PASS' } else { 'BLOCKED' }) `
    $(if ($FirebaseDeliveryVerified) { 'Entrega FCM declarada verificada.' } else { 'Falta evidencia de entrega FCM/deep link real.' })
Add-Check 'physical_smoke' $(if ($PhysicalSmokeVerified) { 'PASS' } else { 'BLOCKED' }) `
    $(if ($PhysicalSmokeVerified) { 'Smoke test físico declarado verificado.' } else { 'Falta instalación/login/nativo/update en Android físico.' })

$blocked = @($checks | Where-Object state -eq 'BLOCKED').Count
$result = [pscustomobject]@{
    ready = $blocked -eq 0
    checkedAtUtc = [DateTime]::UtcNow.ToString('o')
    blocked = $blocked
    checks = $checks
}

if ($Json) {
    $result | ConvertTo-Json -Depth 5
} else {
    $checks | Format-Table -AutoSize
    "RELEASE_READY=$($result.ready) BLOCKED=$blocked"
}

if ($blocked -gt 0) { exit 2 }
