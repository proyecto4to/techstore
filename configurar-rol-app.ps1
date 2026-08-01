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

if (-not (Test-Path $psql)) { throw "No encuentro psql en $psql" }
. (Join-Path $root 'secrets.local.ps1') | Out-Null

$env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD
$admin = 'techstore_user'
$base  = 'techstore_db'

# --- 1. La contrasena del rol nuevo -----------------------------------------
$archivoSecretos = Join-Path $root 'secrets.local.ps1'
$contenido = Get-Content $archivoSecretos -Raw

if ($contenido -match 'APP_DB_PASSWORD') {
    Write-Host '  La contrasena del rol ya estaba en secrets.local.ps1; se reutiliza.'
    . $archivoSecretos | Out-Null
    $passApp = $env:APP_DB_PASSWORD
} else {
    Write-Host '  Generando contrasena para el rol de aplicacion...'
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $passApp = [Convert]::ToBase64String($bytes) -replace '[^a-zA-Z0-9]', ''
    Add-Content $archivoSecretos "`n# Rol de la aplicacion (sin privilegios de administrador)."
    Add-Content $archivoSecretos "`$env:APP_DB_PASSWORD = '$passApp'"
    Write-Host '  Guardada en secrets.local.ps1 (que no se versiona).'
}

# --- 2. El rol y sus permisos ------------------------------------------------
$sql = @"
DO `$`$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'techstore_app') THEN
        CREATE ROLE techstore_app LOGIN PASSWORD '$passApp';
    ELSE
        ALTER ROLE techstore_app PASSWORD '$passApp';
    END IF;
END
`$`$;

-- Explicito, aunque sean los valores por defecto: son justamente los que no
-- queremos que tenga.
ALTER ROLE techstore_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

GRANT CONNECT ON DATABASE $base TO techstore_app;
GRANT USAGE  ON SCHEMA public   TO techstore_app;

-- Datos si, estructura no: las tablas las crea y modifica Flyway con el rol
-- administrador.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA public TO techstore_app;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA public TO techstore_app;

-- Que las tablas que cree una migracion futura tambien queden accesibles.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES    TO techstore_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT                  ON SEQUENCES TO techstore_app;
"@

Write-Host '  Creando el rol y asignando permisos...'
$sql | & $psql -h localhost -U $admin -d $base -v ON_ERROR_STOP=1 -q
if ($LASTEXITCODE -ne 0) { throw 'Fallo la creacion del rol' }

# --- 3. Verificacion ---------------------------------------------------------
Write-Host ''
Write-Host '  Verificando:' -ForegroundColor Cyan
& $psql -h localhost -U $admin -d $base -tAc @"
SELECT '    superusuario: '||rolsuper||'  |  bypassrls: '||rolbypassrls||'  |  crea roles: '||rolcreaterole
FROM pg_roles WHERE rolname='techstore_app';
"@

Write-Host ''
Write-Host '  Rol listo.' -ForegroundColor Green
Write-Host '  Falta un paso: apuntar la aplicacion a este rol.'
Write-Host '  En iniciar-techstore.ps1, el backend debe arrancar con:'
Write-Host '      $env:SPRING_DATASOURCE_USERNAME = "techstore_app"' -ForegroundColor Yellow
Write-Host '      $env:SPRING_DATASOURCE_PASSWORD = $env:APP_DB_PASSWORD' -ForegroundColor Yellow
Write-Host '  Flyway sigue corriendo con el rol administrador para poder migrar.'
