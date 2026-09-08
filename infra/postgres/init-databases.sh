#!/bin/bash
# Створює окрему базу для кожного сервісу і вмикає PostGIS там, де він потрібен.
# Виконується один раз, при першій ініціалізації тому pgdata.
# Якщо міняєш список баз — потрібен `docker compose down -v`.
set -euo pipefail

for db in $(echo "${TP_DATABASES}" | tr ',' ' '); do
  echo "==> creating database ${db}"
  psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname postgres <<-EOSQL
    CREATE DATABASE ${db} OWNER ${POSTGRES_USER};
EOSQL
done

# PostGIS потрібен лише place-service — не вмикаємо розширення там,
# де воно не використовується.
echo "==> enabling postgis on placedb"
psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname placedb <<-'EOSQL'
  CREATE EXTENSION IF NOT EXISTS postgis;
  CREATE EXTENSION IF NOT EXISTS pg_trgm;
EOSQL

echo "==> databases ready"
