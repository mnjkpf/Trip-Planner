# ADR-0001: API Gateway як єдина точка входу з пробросом identity

- Статус: Accepted
- Дата: 2026-09-26

## Context

Сервіси (user, place, trip, context, planner) кожен мав власний порт. Клієнту незручно
й небезпечно ходити в кожен напряму, а перевіряти JWT у кожному сервісі — дублювання.
trip/user/wishlist очікують `X-User-Id` як джерело ідентичності.

## Decision

Ввести `api-gateway` (Spring Cloud Gateway на WebFlux) як єдину точку входу (порт 8080):

- маршрутизація `/api/**` на відповідні сервіси (без StripPrefix — downstream чекають повний шлях);
- централізована валідація JWT (OAuth2 resource server) через JWKS user-service (`jwk-set-uri`),
  ключі не тримаються в gateway;
- `IdentityRelayFilter` (GlobalFilter) читає `uid`/`role` з валідованого токена й проставляє
  заголовки `X-User-Id`/`X-User-Role` downstream, і ПРИБИРАЄ ці заголовки з вхідного запиту
  (клієнт не може їх підробити — джерело істини лише підписаний токен);
- публічні шляхи: `/api/auth/**`, `/api/user/register-user`, JWKS, health.

## Consequences

- Downstream-сервіси довіряють `X-User-Id` і не займаються перевіркою токенів.
- Спуфінг identity неможливий (заголовки затираються на краю).
- Gateway — критичний компонент; його стан і трейси спостерігаються (див. ADR-0003).

## Alternatives

- Перевірка JWT у кожному сервісі — дублювання логіки й ключів, відкинуто.
- Передавати весь JWT downstream — сервіси мусили б його парсити; проброс мінімального
  `X-User-Id` простіший і достатній.
