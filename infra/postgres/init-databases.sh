#!/bin/bash
set -euo pipefail
for db in $(echo "${TP_DATABASES}" | tr ',' ' '); do
  echo "==> creating database ${db}"
  psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname postgres <<-EOSQL
    CREATE DATABASE ${db} OWNER ${POSTGRES_USER};
EOSQL
done
echo "==> enabling postgis on placedb"
psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname placedb <<-'EOSQL'
  CREATE EXTENSION IF NOT EXISTS postgis;
  CREATE EXTENSION IF NOT EXISTS pg_trgm;
EOSQL
echo "==> databases ready"
