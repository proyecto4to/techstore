#!/bin/sh
set -eu

: "${POSTGRES_USER:?Falta POSTGRES_USER}"
: "${POSTGRES_PASSWORD:?Falta POSTGRES_PASSWORD}"
: "${POSTGRES_DB:?Falta POSTGRES_DB}"
: "${APP_DB_PASSWORD:?Falta APP_DB_PASSWORD}"

export PGPASSWORD="$POSTGRES_PASSWORD"

psql \
  --host=postgres \
  --username="$POSTGRES_USER" \
  --dbname="$POSTGRES_DB" \
  --set=ON_ERROR_STOP=1 \
  --set=app_password="$APP_DB_PASSWORD" \
  --set=database_name="$POSTGRES_DB" \
  --set=admin_user="$POSTGRES_USER" <<'SQL'
SELECT format('CREATE ROLE techstore_app LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'techstore_app') \gexec

SELECT format(
  'ALTER ROLE techstore_app LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS',
  :'app_password'
) \gexec

GRANT CONNECT ON DATABASE :"database_name" TO techstore_app;
GRANT USAGE ON SCHEMA public TO techstore_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO techstore_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO techstore_app;

ALTER DEFAULT PRIVILEGES FOR ROLE :"admin_user" IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO techstore_app;
ALTER DEFAULT PRIVILEGES FOR ROLE :"admin_user" IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO techstore_app;
SQL

unset PGPASSWORD
