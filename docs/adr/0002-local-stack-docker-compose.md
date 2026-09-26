# ADR-0002: Локальний стек через Docker Compose

- Статус: Accepted
- Дата: 2026-09-26

## Context

Потрібно піднімати всю систему (6 сервісів + Postgres/Redis/Kafka + спостережуваність)
однією командою для демо й локальної розробки, не ламаючи звичного циклу "сервіси з IDE".

## Decision

- Кожен сервіс — multi-stage Dockerfile (maven:21 будує fat-jar із BuildKit-кешем `.m2`,
  eclipse-temurin:21-jre запускає). Тести в образі не ганяються (`-Dmaven.test.skip=true`),
  бо їх перевіряє `mvnw verify` / CI.
- App-сервіси у `docker-compose.yml` під профілем `full`: `docker compose up -d` лишається
  IDE-режимом (лише інфра), а `docker compose --profile full up -d --build` піднімає все.
- Health-check кожного app-сервіса (перевірка порту через bash `/dev/tcp`, без curl в образі),
  залежності чекають `service_healthy` — `up` завершується, коли сервіси реально готові.

## Consequences

- Немає гонки готовності (dependents стартують після healthy залежностей).
- Розробник обирає режим одним прапорцем `--profile full`.
- Перша збірка тягне залежності; далі BuildKit-кеш `.m2` спільний між сервісами.

## Alternatives

- Kubernetes/Helm — надлишково для локального pet-проєкту.
- Актуаторний health через curl у healthcheck — вимагав би ставити curl в образ; `/dev/tcp`
  не потребує додаткових пакетів.
