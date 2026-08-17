# Crea el rol con el que la aplicacion se conecta a PostgreSQL.
#
# POR QUE HACE FALTA
# Hasta ahora la aplicacion conectaba como techstore_user, que es SUPERUSUARIO.
# Eso trae dos problemas:
#
#   1. Row Level Security no se le aplica. Un superusuario tiene BYPASSRLS: para
#      el motor puede ver todas las filas de todas las empresas, asi que las
#      politicas de aislamiento quedan decorativas.
#
#   2. Si alguna vez hubiera una inyeccion SQL, el dano no termina en la base:
#      un superusuario puede ejecutar COPY ... FROM PROGRAM y correr comandos
#      del sistema operativo.
#
# Este script crea techstore_app: puede leer y escribir los datos, y nada mas.
# No crea tablas, no crea roles, no se saltea RLS.
#
# Es idempotente: se puede correr las veces que haga falta.

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$psql = Join-Path $root 'tools\pgsql\bin\psql.exe'
$archivoSecretos = Join-Path $root 'secrets.local.ps1'

if (-not (Test-Path -LiteralPath $psql)) { throw "No encuentro psql en $psql" }
if (-not (Test-Path -LiteralPath $archivoSecretos)) {
    throw 'Falta secrets.local.ps1. Copia primero secrets.local.ps1.ejemplo.'
}
. $archivoSecretos | Out-Null

$admin = 'techstore_user'
$base  = 'techstore_db'
if ([string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_PASSWORD) -or
    $env:SPRING_DATASOURCE_PASSWORD.Length -lt 16 -or
    $env:SPRING_DATASOURCE_PASSWORD -match 'reemplaza|techstore_pass_dev') {
    throw 'SPRING_DATASOURCE_PASSWORD falta o sigue siendo insegura.'
}
$passwordAnterior = $env:PGPASSWORD
$env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD

# --- 1. La contrasena del rol nuevo -----------------------------------------
$contenido = Get-Content -LiteralPath $archivoSecretos -Raw

if ($contenido -match 'APP_DB_PASSWORD') {
    Write-Host '  La contrasena del rol ya estaba en secrets.local.ps1; se reutiliza.'
    . $archivoSecretos | Out-Null
    $passApp = $env:APP_DB_PASSWORD
} else {
    Write-Host '  Generando contrasena para el rol de aplicacion...'
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $passApp = [Convert]::ToBase64String($bytes) -replace '[^a-zA-Z0-9]', ''
    Add-Content -LiteralPath $archivoSecretos "`n# Rol de la aplicacion (sin privilegios de administrador)."
    Add-Content -LiteralPath $archivoSecretos "`$env:APP_DB_PASSWORD = '$passApp'"
    Write-Host '  Guardada en secrets.local.ps1 (que no se versiona).'
}

if ([string]::IsNullOrWhiteSpace($passApp) -or $passApp.Length -lt 16 -or
    $passApp -match '[\r\n]' -or $passApp -match 'reemplaza' -or
    $passApp -eq $env:SPRING_DATASOURCE_PASSWORD) {
    throw 'APP_DB_PASSWORD debe tener 16+ caracteres, no contener saltos y ser distinta de la clave administrativa.'
}

# --- 2. El rol y sus permisos ------------------------------------------------
$sql = @'
\getenv app_password APP_DB_PASSWORD

SELECT format('CREATE ROLE techstore_app LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'techstore_app') \gexec

SELECT format(
    'ALTER ROLE techstore_app LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS',
    :'app_password'
) \gexec

-- Explicito, aunque sean los valores por defecto: son justamente los que no
-- queremos que tenga.
GRANT CONNECT ON DATABASE :"database_name" TO techstore_app;
GRANT USAGE ON SCHEMA public TO techstore_app;

-- Datos si, estructura no: las tablas las crea y modifica Flyway con el rol
-- administrador.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA public TO techstore_app;
GRANT USAGE, SELECT, UPDATE          ON ALL SEQUENCES IN SCHEMA public TO techstore_app;

-- Que las tablas que cree una migracion futura tambien queden accesibles.
ALTER DEFAULT PRIVILEGES FOR ROLE :"admin_user" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES    TO techstore_app;
ALTER DEFAULT PRIVILEGES FOR ROLE :"admin_user" IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE          ON SEQUENCES TO techstore_app;
'@

Write-Host '  Creando el rol y asignando permisos...'
try {
    $sql | & $psql -h localhost -U $admin -d $base --set=ON_ERROR_STOP=1 --set="database_name=$base" --set="admin_user=$admin" -q
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la creacion del rol' }

# --- 3. Verificacion ---------------------------------------------------------
    Write-Host ''
    Write-Host '  Verificando:' -ForegroundColor Cyan
    & $psql -h localhost -U $admin -d $base -tAc @"
SELECT '    superusuario: '||rolsuper||'  |  bypassrls: '||rolbypassrls||'  |  crea roles: '||rolcreaterole
FROM pg_roles WHERE rolname='techstore_app';
"@
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo verificar el rol techstore_app' }
} finally {
    if ($null -eq $passwordAnterior) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
    else { $env:PGPASSWORD = $passwordAnterior }
}

Write-Host ''
Write-Host '  Rol listo.' -ForegroundColor Green
Write-Host '  El acceso directo ya usara techstore_app para la aplicacion y el rol'
Write-Host '  administrador solamente para Flyway y respaldos.'
