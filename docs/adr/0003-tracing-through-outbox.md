# ADR-0003: Наскрізний трейсинг і traceparent через outbox

- Статус: Accepted
- Дата: 2026-09-26

## Context

Система поєднує синхронний HTTP (gateway -> сервіси) і асинхронний Kafka (trip -> planner ->
context/place -> trip). Потрібна наскрізна спостережуваність. Особливість: trip-service
використовує outbox pattern — подія публікується окремим планувальником ПІЗНІШЕ за HTTP-запит,
тож in-process trace-контекст природно розривається, і HTTP-запит та async-петля були б
різними трейсами.

## Decision

- Усі сервіси шлють трейси/метрики по OTLP у `grafana/otel-lgtm` (Tempo + Prometheus + Grafana).
  Micrometer Observation інструментує HTTP (RestClient) і Kafka (`observation-enabled`).
- Проброс через outbox: у `requestPlan` знімаємо поточний `traceparent` (Micrometer `Tracer`)
  і кладемо в `outbox.headers`. `OutboxPublisher` відновлює контекст навколо `send`
  (`currentTraceContext().newScope(...)`) — observation-продюсер інжектить traceparent із тим
  самим trace-id, і consumer продовжує ТОЙ САМИЙ трейс.

Результат: один трейс від `POST /api/trips/{id}/plan` через 5 сервісів, по обидва боки
sync/async межі (підтверджено в Tempo).

## Consequences

- Повна причинно-наслідкова картина запиту, включно з розривом на outbox.
- Планувальник має Micrometer `Tracer` (з OTel-стартера); знімання/відновлення контексту
  null-safe (`ObjectProvider`) — тести без трейсингу не ламаються.

## Alternatives

- Spring Cloud Sleuth — застарілий на користь Micrometer Tracing.
- Не проносити контекст — простіше, але HTTP-запит і петля лишалися б окремими трейсами.
