[CmdletBinding()]
param(
    [string]$ApiBaseUrl,
    [string]$ApkPath,
    [switch]$PhysicalNativeVerified,
    [switch]$PrivateUpdateVerified,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$mobile = Join-Path $root 'techstore-mobile'
$android = Join-Path $mobile 'android'
$metadataPath = Join-Path $root 'artifacts\mobile\private\private-release-metadata.json'
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][ValidateSet('PASS', 'BLOCKED')][string]$State,
        [Parameter(Mandatory = $true)][string]$Detail
    )
    $checks.Add([pscustomobject]@{ check = $Name; state = $State; detail = $Detail })
}

function Test-PrivateIpv4([string]$HostName) {
    $address = $null
    if (-not [Net.IPAddress]::TryParse($HostName, [ref]$address) -or
        $address.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) {
        return $false
    }
    $bytes = $address.GetAddressBytes()
    return $bytes[0] -eq 10 -or
        ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or
        ($bytes[0] -eq 192 -and $bytes[1] -eq 168)
}

function Read-SimpleProperties([string]$Path) {
    $properties = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }
        $properties[$trimmed.Substring(0, $separator).Trim()] = $trimmed.Substring($separator + 1).Trim()
    }
    return $properties
}

function Find-Keytool {
    $command = Get-Command keytool -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    $candidate = 'C:\Program Files\Java\jdk-22\bin\keytool.exe'
    if (Test-Path -LiteralPath $candidate) { return $candidate }
    return $null
}

function Find-ApkSigner {
    $sdk = if ($env:ANDROID_SDK_ROOT) {
        $env:ANDROID_SDK_ROOT
    } elseif ($env:ANDROID_HOME) {
        $env:ANDROID_HOME
    } else {
        'C:\Android\Sdk'
    }
    $toolsRoot = Join-Path $sdk 'build-tools'
    if (-not (Test-Path -LiteralPath $toolsRoot)) { return $null }
    $version = Get-ChildItem -LiteralPath $toolsRoot -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    if ($null -eq $version) { return $null }
    $candidate = Join-Path $version.FullName 'apksigner.bat'
    if (Test-Path -LiteralPath $candidate) { return $candidate }
    return $null
}

$metadata = $null
if (-not (Test-Path -LiteralPath $metadataPath)) {
    Add-Check 'private_metadata' 'BLOCKED' 'Falta private-release-metadata.json.'
} else {
    try {
        $metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding utf8 | ConvertFrom-Json
        if ($metadata.distribution -ne 'direct-apk' -or
            $metadata.applicationId -ne 'py.com.techstore.app.private') {
            throw 'Los metadatos no corresponden al flavor privado.'
        }
        Add-Check 'private_metadata' 'PASS' "Versión $($metadata.versionName)+$($metadata.versionCode), direct-apk."
    } catch {
        Add-Check 'private_metadata' 'BLOCKED' $_.Exception.Message
    }
}

if ([string]::IsNullOrWhiteSpace($ApiBaseUrl) -and $null -ne $metadata) {
    $ApiBaseUrl = $metadata.apiBaseUrl
}
$apiUri = $null
if ([string]::IsNullOrWhiteSpace($ApiBaseUrl) -or
    -not [Uri]::TryCreate($ApiBaseUrl, [UriKind]::Absolute, [ref]$apiUri) -or
    $apiUri.Scheme -notin @('http', 'https') -or
    ($apiUri.Scheme -eq 'http' -and -not (Test-PrivateIpv4 $apiUri.Host))) {
    Add-Check 'private_api' 'BLOCKED' 'La API debe ser HTTPS o HTTP hacia una IPv4 privada RFC1918.'
} else {
    try {
        $health = Invoke-RestMethod -Uri ($ApiBaseUrl.TrimEnd('/') + '/actuator/health') -TimeoutSec 5
        if ($health.status -ne 'UP') { throw 'El backend no respondió UP.' }
        Add-Check 'private_api' 'PASS' "Backend UP en $ApiBaseUrl."
    } catch {
        Add-Check 'private_api' 'BLOCKED' "Backend inaccesible: $($_.Exception.Message)"
    }
}

$keyPropertiesPath = Join-Path $android 'key.properties'
if (-not (Test-Path -LiteralPath $keyPropertiesPath)) {
    Add-Check 'private_signing' 'BLOCKED' 'Falta android/key.properties.'
} else {
    try {
        $properties = Read-SimpleProperties $keyPropertiesPath
        foreach ($required in @('storeFile', 'storePassword', 'keyAlias', 'keyPassword')) {
            if (-not $properties.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($properties[$required])) {
                throw "Falta $required en key.properties."
            }
        }
        $storeFile = $properties.storeFile
        if (-not [IO.Path]::IsPathRooted($storeFile)) {
            $storeFile = [IO.Path]::GetFullPath((Join-Path (Join-Path $android 'app') $storeFile))
        }
        if (-not (Test-Path -LiteralPath $storeFile -PathType Leaf)) { throw 'El keystore no existe.' }
        $keytool = Find-Keytool
        if ([string]::IsNullOrWhiteSpace($keytool)) { throw 'No se encontró keytool.' }
        $passwordVariable = 'TECHSTORE_PRIVATE_READINESS_PASSWORD'
        $previousPassword = [Environment]::GetEnvironmentVariable($passwordVariable, 'Process')
        try {
            [Environment]::SetEnvironmentVariable($passwordVariable, $properties.storePassword, 'Process')
            & $keytool -list -keystore $storeFile '-storepass:env' $passwordVariable -alias $properties.keyAlias *> $null
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo validar el alias privado.' }
        } finally {
            [Environment]::SetEnvironmentVariable($passwordVariable, $previousPassword, 'Process')
        }
        Add-Check 'private_signing' 'PASS' 'Keystore y alias privados válidos; las claves no se imprimieron.'
    } catch {
        Add-Check 'private_signing' 'BLOCKED' $_.Exception.Message
    }
}

if ([string]::IsNullOrWhiteSpace($ApkPath) -and $null -ne $metadata) { $ApkPath = $metadata.apk }
if ([string]::IsNullOrWhiteSpace($ApkPath) -or -not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
    Add-Check 'private_apk' 'BLOCKED' 'No se encontró el APK privado.'
} else {
    try {
        $hash = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($null -eq $metadata -or $hash -ne $metadata.apkSha256) { throw 'El SHA-256 no coincide con los metadatos.' }
        $apksigner = Find-ApkSigner
        if ([string]::IsNullOrWhiteSpace($apksigner)) { throw 'No se encontró apksigner.' }
        $signature = @(& $apksigner verify --verbose --print-certs $ApkPath 2>&1)
        if ($LASTEXITCODE -ne 0) { throw 'La firma APK no es válida.' }
        $certificate = ($signature | Select-String 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)' |
            Select-Object -First 1).Matches.Groups[1].Value.ToLowerInvariant()
        if (-not $certificate -or $certificate -ne $metadata.signingCertificateSha256) {
            throw 'El certificado APK no coincide con los metadatos.'
        }
        if (($signature -join "`n") -match 'Android Debug') { throw 'El APK usa certificado debug.' }
        Add-Check 'private_apk' 'PASS' "Hash y firma válidos: $([IO.Path]::GetFileName($ApkPath))."
    } catch {
        Add-Check 'private_apk' 'BLOCKED' $_.Exception.Message
    }
}

$adb = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $adb -or $null -eq $metadata) {
    Add-Check 'physical_install' 'BLOCKED' 'ADB o metadatos no disponibles.'
} else {
    try {
        $devices = @(& $adb.Source devices -l 2>$null | Where-Object { $_ -match '^\S+\s+device\b' -and $_ -notmatch '^emulator-' })
        if ($devices.Count -lt 1) { throw 'No hay Android físico autorizado.' }
        $serial = ($devices[0] -split '\s+')[0]
        $package = @(& $adb.Source -s $serial shell dumpsys package py.com.techstore.app.private 2>$null)
        if (($package -join "`n") -notmatch "versionName=$([regex]::Escape([string]$metadata.versionName))\b" -or
            ($package -join "`n") -notmatch "versionCode=$($metadata.versionCode)\b") {
            throw 'La versión instalada no coincide con el APK final.'
        }
        Add-Check 'physical_install' 'PASS' "$serial tiene TechStore $($metadata.versionName)+$($metadata.versionCode)."
    } catch {
        Add-Check 'physical_install' 'BLOCKED' $_.Exception.Message
    }
}

Add-Check 'physical_native' $(if ($PhysicalNativeVerified) { 'PASS' } else { 'BLOCKED' }) `
    $(if ($PhysicalNativeVerified) { 'Cámara, biometría, ciclo de vida, offline y reconexión declarados verificados.' } else { 'Falta declarar la evidencia nativa física.' })
Add-Check 'private_update' $(if ($PrivateUpdateVerified) { 'PASS' } else { 'BLOCKED' }) `
    $(if ($PrivateUpdateVerified) { 'Actualizacion directa firmada v1-a-v2 declarada verificada.' } else { 'Falta verificar una actualizacion directa conservando datos.' })

$blocked = @($checks | Where-Object state -eq 'BLOCKED').Count
$result = [pscustomobject]@{
    ready = $blocked -eq 0
    distribution = 'direct-apk'
    checkedAtUtc = [DateTime]::UtcNow.ToString('o')
    blocked = $blocked
    checks = $checks
}

if ($Json) {
    $result | ConvertTo-Json -Depth 5
} else {
    $checks | Format-Table -AutoSize
    "PRIVATE_RELEASE_READY=$($result.ready) BLOCKED=$blocked"
}

if ($blocked -gt 0) { exit 2 }
