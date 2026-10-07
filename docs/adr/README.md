# Architecture Decision Records

Короткі записи ключових архітектурних рішень проєкту. Формат: Context / Decision /
Consequences / Alternatives. Кожен ADR незмінний після прийняття; нове рішення —
новий запис (за потреби зі статусом "Superseded by ADR-XXXX").

| ADR | Тема | Статус |
|-----|------|--------|
| [0001](0001-api-gateway-identity-relay.md) | API Gateway: єдина точка входу, JWT, проброс identity | Accepted |
| [0002](0002-local-stack-docker-compose.md) | Локальний стек через Docker Compose (profiles, health-checks) | Accepted |
| [0003](0003-tracing-through-outbox.md) | Наскрізний трейсинг і traceparent через outbox | Accepted |
| [0004](0004-resilience-circuit-breaker-and-rate-limit.md) | Resilience: circuit breaker + rate limiting | Accepted |
| [0005](0005-realtime-plan-updates-sse.md) | Реальний час: SSE замість поллінга | Accepted |
