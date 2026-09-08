[CmdletBinding()]
param(
    [string]$Alias = 'techstore-private',
    [string]$DistinguishedName = 'CN=TechStore Private, OU=Mobile, O=TechStore, L=Asuncion, ST=Central, C=PY'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$android = Join-Path $root 'techstore-mobile\android'
$keyProperties = Join-Path $android 'key.properties'
$signingDir = Join-Path $env:LOCALAPPDATA 'TechStore\mobile-signing'
$keystore = Join-Path $signingDir 'techstore-private.p12'
$certificate = Join-Path $signingDir 'techstore-private.cer'
$credentialsBackup = Join-Path $signingDir 'techstore-private.credentials.properties'

if (Test-Path -LiteralPath $keyProperties) {
    throw "Ya existe $keyProperties. No se reemplazó ninguna firma."
}
if (Test-Path -LiteralPath $keystore) {
    throw "Ya existe $keystore sin key.properties. Revisá la custodia antes de continuar."
}

$keytoolCandidates = @(
    $(if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\keytool.exe' }),
    (Join-Path (Split-Path -Parent $PSScriptRoot) 'tools\jdk-21\bin\keytool.exe'),
    'C:\Program Files\Java\jdk-22\bin\keytool.exe',
    'C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot\bin\keytool.exe'
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
$keytool = $keytoolCandidates | Select-Object -First 1
if (-not $keytool) { throw 'No se encontró keytool.' }

New-Item -ItemType Directory -Path $signingDir -Force | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $signingDir '/inheritance:r' '/grant:r' "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' *> $null
if ($LASTEXITCODE -ne 0) { throw 'No se pudo restringir el directorio de firma.' }

$bytes = [byte[]]::new(32)
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
$password = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$passwordVariable = 'TECHSTORE_PRIVATE_KEY_PASSWORD'
$previousPassword = [Environment]::GetEnvironmentVariable($passwordVariable, 'Process')

try {
    [Environment]::SetEnvironmentVariable($passwordVariable, $password, 'Process')
    $ErrorActionPreference = 'Continue'
    try {
        & $keytool -genkeypair -noprompt `
            -alias $Alias `
            -keyalg RSA `
            -keysize 4096 `
            -validity 9125 `
            -dname $DistinguishedName `
            -keystore $keystore `
            -storetype PKCS12 `
            '-storepass:env' $passwordVariable `
            '-keypass:env' $passwordVariable 2>$null | Out-Null
        $keytoolExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = 'Stop'
    }
    if ($keytoolExit -ne 0) { throw 'keytool no pudo crear la firma privada.' }

    $ErrorActionPreference = 'Continue'
    try {
        & $keytool -list -keystore $keystore '-storepass:env' $passwordVariable -alias $Alias `
            2>$null | Out-Null
        $keytoolExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = 'Stop'
    }
    if ($keytoolExit -ne 0) { throw 'No se pudo validar el alias generado.' }

    $ErrorActionPreference = 'Continue'
    try {
        & $keytool -exportcert -keystore $keystore '-storepass:env' $passwordVariable `
            -alias $Alias -file $certificate 2>$null | Out-Null
        $keytoolExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = 'Stop'
    }
    if ($keytoolExit -ne 0) { throw 'No se pudo exportar el certificado público.' }

    $normalizedStore = $keystore.Replace('\', '/')
    $properties = @(
        "storeFile=$normalizedStore",
        "storePassword=$password",
        "keyAlias=$Alias",
        "keyPassword=$password"
    ) -join [Environment]::NewLine
    $encoding = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText($keyProperties, $properties + [Environment]::NewLine, $encoding)
    [IO.File]::WriteAllText($credentialsBackup, $properties + [Environment]::NewLine, $encoding)

    foreach ($protected in @($keyProperties, $keystore, $certificate, $credentialsBackup)) {
        & icacls.exe $protected '/inheritance:r' '/grant:r' "${identity}:F" 'SYSTEM:F' *> $null
        if ($LASTEXITCODE -ne 0) { throw "No se pudo restringir $protected" }
    }

    $cert = [Security.Cryptography.X509Certificates.X509Certificate2]::new($certificate)
    try {
        $fingerprint = $cert.GetCertHashString([Security.Cryptography.HashAlgorithmName]::SHA256).ToLowerInvariant()
        [pscustomobject]@{
            status = 'CREATED'
            keystore = $keystore
            keyProperties = $keyProperties
            credentialsBackup = $credentialsBackup
            certificate = $certificate
            alias = $Alias
            certificateSha256 = $fingerprint
            expires = $cert.NotAfter.ToString('yyyy-MM-dd')
        } | Format-List
    } finally {
        $cert.Dispose()
    }
} catch {
    foreach ($created in @($keyProperties, $credentialsBackup, $certificate, $keystore)) {
        if (Test-Path -LiteralPath $created) { Remove-Item -LiteralPath $created -Force }
    }
    throw
} finally {
    [Environment]::SetEnvironmentVariable($passwordVariable, $previousPassword, 'Process')
    $password = $null
}
