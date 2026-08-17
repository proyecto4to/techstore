#!/bin/sh
set -efu

fail() {
  echo >&2 "$1"
  exit 1
}

: "${TECHSTORE_MODE:?Falta TECHSTORE_MODE}"

# Los guards asociados a cada perfil llegan por esta rama. Aunque alguien use
# --profile para agregar otro frontend al elegido en COMPOSE_PROFILES, el
# frontend incorrecto queda bloqueado antes de arrancar.
if [ -n "${EXPECTED_MODE:-}" ]; then
  [ "$TECHSTORE_MODE" = "$EXPECTED_MODE" ] ||
    fail "El perfil de frontend '$EXPECTED_MODE' no coincide con TECHSTORE_MODE='$TECHSTORE_MODE'"
  exit 0
fi

: "${SELECTED_FRONTEND_PROFILE:?Falta COMPOSE_PROFILES}"
: "${POSTGRES_PASSWORD:?Falta POSTGRES_PASSWORD}"
: "${APP_DB_PASSWORD:?Falta APP_DB_PASSWORD}"
: "${JWT_SECRET:?Falta JWT_SECRET}"
: "${SPRING_PROFILES_ACTIVE:?Falta SPRING_PROFILES_ACTIVE}"
: "${SPRINGDOC_ENABLED:?Falta SPRINGDOC_ENABLED}"
: "${PUBLIC_BASE_URL:?Falta PUBLIC_BASE_URL}"
: "${CORS_ALLOWED_ORIGINS:?Falta CORS_ALLOWED_ORIGINS}"

validar_admin() {
  [ "${#BOOTSTRAP_ADMIN_PASSWORD}" -ge 12 ] ||
    fail "$TECHSTORE_MODE requiere BOOTSTRAP_ADMIN_PASSWORD de 12+ caracteres"
  case "$BOOTSTRAP_ADMIN_PASSWORD" in
    *[a-z]*[A-Z]*[0-9]*|*[a-z]*[0-9]*[A-Z]*|*[A-Z]*[a-z]*[0-9]*|*[A-Z]*[0-9]*[a-z]*|*[0-9]*[a-z]*[A-Z]*|*[0-9]*[A-Z]*[a-z]*) ;;
    *) fail "BOOTSTRAP_ADMIN_PASSWORD requiere mayuscula, minuscula y numero" ;;
  esac
  [ "$BOOTSTRAP_ADMIN_PASSWORD" != "admin123" ] || fail "No uses admin123 fuera del modo local"
  [ "$BOOTSTRAP_ADMIN_PASSWORD" != "ReemplazaEstaClaveInicial2026" ] || fail "Reemplaza BOOTSTRAP_ADMIN_PASSWORD"
  [ "$BOOTSTRAP_ADMIN_PASSWORD" != "$POSTGRES_PASSWORD" ] || fail "La clave inicial del admin debe diferir de POSTGRES_PASSWORD"
  [ "$BOOTSTRAP_ADMIN_PASSWORD" != "$APP_DB_PASSWORD" ] || fail "La clave inicial del admin debe diferir de APP_DB_PASSWORD"
  [ "$BOOTSTRAP_ADMIN_PASSWORD" != "$JWT_SECRET" ] || fail "La clave inicial del admin debe diferir de JWT_SECRET"
}

validar_loopback() {
  [ "$POSTGRES_BIND_ADDRESS" = "127.0.0.1" ] || fail "$TECHSTORE_MODE requiere POSTGRES_BIND_ADDRESS=127.0.0.1"
  [ "$BACKEND_BIND_ADDRESS" = "127.0.0.1" ] || fail "$TECHSTORE_MODE requiere BACKEND_BIND_ADDRESS=127.0.0.1"
  [ "$FRONTEND_BIND_ADDRESS" = "127.0.0.1" ] || fail "$TECHSTORE_MODE requiere FRONTEND_BIND_ADDRESS=127.0.0.1"
}

[ "${#POSTGRES_PASSWORD}" -ge 16 ] || fail "POSTGRES_PASSWORD debe tener al menos 16 caracteres"
[ "${#APP_DB_PASSWORD}" -ge 16 ] || fail "APP_DB_PASSWORD debe tener al menos 16 caracteres"
[ "${#JWT_SECRET}" -ge 32 ] || fail "JWT_SECRET debe tener al menos 32 caracteres"
[ "$POSTGRES_PASSWORD" != "reemplaza-esto-por-una-password-fuerte" ] || fail "Reemplaza POSTGRES_PASSWORD"
[ "$APP_DB_PASSWORD" != "reemplaza-esto-por-otra-password-fuerte-y-distinta" ] || fail "Reemplaza APP_DB_PASSWORD"
[ "$JWT_SECRET" != "reemplaza-esto-por-una-cadena-aleatoria-de-32-caracteres-o-mas" ] || fail "Reemplaza JWT_SECRET"
[ "$POSTGRES_PASSWORD" != "$APP_DB_PASSWORD" ] || fail "Las contrasenas administrativa y runtime deben ser distintas"
[ "$POSTGRES_PASSWORD" != "$JWT_SECRET" ] || fail "JWT_SECRET debe diferir de POSTGRES_PASSWORD"
[ "$APP_DB_PASSWORD" != "$JWT_SECRET" ] || fail "JWT_SECRET debe diferir de APP_DB_PASSWORD"
[ "$SELECTED_FRONTEND_PROFILE" = "$TECHSTORE_MODE" ] || fail "COMPOSE_PROFILES debe coincidir exactamente con TECHSTORE_MODE"

case "$TECHSTORE_MODE" in
  local)
    [ "$SPRING_PROFILES_ACTIVE" = "dev" ] || fail "local requiere SPRING_PROFILES_ACTIVE=dev"
    [ "$SPRINGDOC_ENABLED" = "true" ] || fail "local requiere SPRINGDOC_ENABLED=true"
    case "$PUBLIC_BASE_URL" in
      http://localhost|http://localhost:*|http://127.0.0.1|http://127.0.0.1:*) ;;
      *) fail "local requiere PUBLIC_BASE_URL en localhost" ;;
    esac
    ;;
  commercial-local)
    [ "$SPRING_PROFILES_ACTIVE" = "prod" ] || fail "commercial-local requiere SPRING_PROFILES_ACTIVE=prod"
    [ "$SPRINGDOC_ENABLED" = "false" ] || fail "commercial-local requiere SPRINGDOC_ENABLED=false"
    validar_admin
    validar_loopback
    case "$PUBLIC_BASE_URL" in
      http://localhost|http://localhost:*|http://127.0.0.1|http://127.0.0.1:*) ;;
      *) fail "commercial-local solo permite PUBLIC_BASE_URL HTTP en localhost" ;;
    esac
    [ "$CORS_ALLOWED_ORIGINS" = "$PUBLIC_BASE_URL" ] ||
      fail "commercial-local requiere CORS_ALLOWED_ORIGINS igual a PUBLIC_BASE_URL"
    ;;
  commercial)
    [ "$SPRING_PROFILES_ACTIVE" = "prod" ] || fail "commercial requiere SPRING_PROFILES_ACTIVE=prod"
    [ "$SPRINGDOC_ENABLED" = "false" ] || fail "commercial requiere SPRINGDOC_ENABLED=false"
    validar_admin
    validar_loopback
    case "$PUBLIC_BASE_URL" in
      https://?*) ;;
      *) fail "commercial requiere PUBLIC_BASE_URL=https://... y un proxy TLS" ;;
    esac
    IFS_ANTERIOR=$IFS
    IFS=','
    for origen in $CORS_ALLOWED_ORIGINS; do
      case "$origen" in
        https://?*) ;;
        *) fail "commercial requiere que todos los origenes CORS usen HTTPS" ;;
      esac
    done
    IFS=$IFS_ANTERIOR
    ;;
  *)
    fail "TECHSTORE_MODE debe ser local, commercial-local o commercial"
    ;;
esac
