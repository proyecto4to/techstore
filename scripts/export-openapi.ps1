param(
    [string]$ApiDocsUrl = 'http://localhost:8090/api/v1/api-docs',
    [string]$ApiDocsPath = '',
    [string]$OutputPath = 'docs/api/techstore-v1.openapi.json'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutput = if ([System.IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath
} else {
    Join-Path $repositoryRoot $OutputPath
}

$specification = if ($ApiDocsPath) {
    $resolvedSource = if ([System.IO.Path]::IsPathRooted($ApiDocsPath)) {
        $ApiDocsPath
    } else {
        Join-Path $repositoryRoot $ApiDocsPath
    }
    Get-Content -LiteralPath $resolvedSource -Raw | ConvertFrom-Json
} else {
    Invoke-RestMethod -Uri $ApiDocsUrl -TimeoutSec 30
}
if (-not $specification.openapi -or -not $specification.paths) {
    throw "La respuesta de $ApiDocsUrl no es un documento OpenAPI válido."
}

# El contrato versionado debe ser portable entre development/staging/production.
$specification.servers = @([pscustomobject]@{ url = '/' })

# El backend activo puede ser una versión anterior a las anotaciones fuente.
# Normalizar las operaciones públicas evita que hereden el Bearer global.
$publicOperations = @(
    @('/api/v1/auth/registro', 'post'),
    @('/api/v1/auth/login', 'post'),
    @('/api/v1/auth/refresh', 'post'),
    @('/api/v1/auth/logout', 'post'),
    @('/api/v1/auth/password/forgot', 'post'),
    @('/api/v1/auth/password/reset', 'post'),
    @('/api/v1/productos', 'get'),
    @('/api/v1/productos/{id}', 'get'),
    @('/api/v1/marcas', 'get'),
    @('/api/v1/carrito/validar', 'post')
)
foreach ($entry in $publicOperations) {
    $operation = $specification.paths.($entry[0]).($entry[1])
    if ($null -ne $operation) {
        $operation | Add-Member -NotePropertyName security -NotePropertyValue @() -Force
    }
}

$directory = Split-Path -Parent $resolvedOutput
[System.IO.Directory]::CreateDirectory($directory) | Out-Null
$json = $specification | ConvertTo-Json -Depth 100
[System.IO.File]::WriteAllText($resolvedOutput, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))

$pathCount = @($specification.paths.PSObject.Properties).Count
$operationCount = 0
foreach ($path in $specification.paths.PSObject.Properties) {
    foreach ($candidate in $path.Value.PSObject.Properties) {
        if ($candidate.Name -in @('get', 'post', 'put', 'patch', 'delete', 'options', 'head')) {
            $operationCount++
        }
    }
}

Write-Output "OPENAPI_EXPORTED path=$resolvedOutput paths=$pathCount operations=$operationCount"
