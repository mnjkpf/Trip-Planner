# ADR-0004: Resilience — circuit breaker і rate limiting

- Статус: Accepted
- Дата: 2026-09-26

## Context

planner синхронно ходить у place-service і context-service. Падіння downstream не має
рушити планувальник. Gateway як єдина точка входу має захищатися від зловживань.

## Decision

### Circuit breaker (planner -> place/context)

Використати **Resilience4j core** (не Spring Cloud CircuitBreaker) і обгортати виклики через
`circuitBreaker.executeSupplier(...)` з fallback (порожні місця / порожній контекст).

Ключова причина вибору саме core: дефолтний Spring Cloud CircuitBreaker застосовує TimeLimiter,
що виконує виклик на ОКРЕМОМУ потоці (thread-pool bulkhead) — а це розірвало б наскрізний
trace-контекст (ADR-0003). `executeSupplier` виконується на потоці виклику, тож traceparent
зберігається. Метрики (`resilience4j_circuitbreaker_*`) прив'язані до Micrometer і йдуть у
`/actuator/prometheus` та по OTLP у Grafana.

### Rate limiting (gateway)

`RequestRateLimiter` (default-filter) із `RedisRateLimiter` (token bucket, Redis уже підключений):
10 запитів/с, сплеск до 20. Ключ — користувач (JWT sub) або IP для анонімних; перевищення -> 429.

## Consequences

- Падіння place/context дає деградований маршрут, а не помилку; коло відкривається й швидко
  фейлить, поки downstream лежить.
- Трейсинг лишається цілим (виклики не міняють потік).
- Gateway обмежує зловживання; лічильники розподілені через Redis (працює й за кількох інстансів).

## Alternatives

- Spring Cloud CircuitBreaker (Resilience4j) — ідіоматичніший, але дефолтний thread-pool рве трейс.
- In-memory rate limiter — не переживає рестарт і не працює за кількох інстансів gateway.
