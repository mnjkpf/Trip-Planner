# Waylo

Планувальник подорожей на мікросервісах: вводиш місто й дати — сервіс сам підбирає
місця, розкладає їх по днях у логічному порядку, рахує відстані й час, малює маршрут
по дорогах на Google Maps і показує готелі та авіаквитки на твої дати.

## Що всередині

| Сервіс | Порт | За що відповідає |
|---|---|---|
| `api-gateway` | 8080 | Єдина точка входу, валідація JWT, rate-limit на Redis, relay `X-User-Id` |
| `user-service` | 8081 | Реєстрація/вхід (локальний + Google Sign-In), JWT RS256, JWKS, профіль |
| `place-service` | 8082 | Каталог місць на PostGIS, геокодинг міст, аеропорти, фото з Wikipedia |
| `trip-service` | 8083 | Подорожі, маршрути, вішліст, outbox → Kafka, SSE-оновлення |
| `context-service` | 8084 | Сезон і погода на дати подорожі |
| `planner-service` | 8086 | Асинхронне планування: добір POI, розбиття по днях, nearest-neighbour |
| `offer-service` | 8087 | Готелі та авіаквитки через SerpAPI (Google Hotels / Google Flights) |
| `frontend/web` | 4200 | Angular 22, standalone-компоненти, сигнали, Google Maps, i18n на 7 мов |

Інфраструктура: PostgreSQL + PostGIS, Redis, Kafka (KRaft), Grafana LGTM для
трейсів і метрик по OTLP.

## Запуск

```bash
cp .env.example .env     # заповни ключі (див. нижче)
docker compose --profile full up -d --build
docker compose --profile dev up -d --build frontend-dev
```

Фронт на http://localhost:4200, gateway на http://localhost:8080,
Grafana на http://localhost:3000, Kafka UI на http://localhost:8085.

Альтернатива для фронту (якщо Windows не блокує нативні бінарі):

```bash
cd frontend/web && npm install && npm start
```

## Ключі

Усі опційні — без них відповідна фіча просто вимикається, решта працює.

| Змінна | Для чого | Де взяти |
|---|---|---|
| `GEOAPIFY_API_KEY` | POI-пошук і геокодинг міст | geoapify.com |
| `SERPAPI_API_KEY` | Готелі та авіаквитки | serpapi.com (100 пошуків/міс безкоштовно) |
| `GOOGLE_OAUTH_CLIENT_ID` | Вхід через Google | Google Cloud Console → Credentials |

Google Maps ключ і Map ID живуть у `frontend/web/src/environments/environment.ts`
(цей файл у `.gitignore`; шаблон — `environment.example.ts`).

## Архітектурні рішення

- **Outbox + Kafka** замість прямих викликів між trip- і planner-service:
  подія записується в одній транзакції з даними, публікується окремим поллером.
- **SSE** для прогресу планування — браузер тримає одне з'єднання замість поллінгу.
- **DEFERRABLE INITIALLY DEFERRED** унікальний індекс на порядок пунктів маршруту —
  дозволяє перенумерувати їх усередині однієї транзакції без тимчасових колізій.
- **Resilience4j** circuit breaker навколо зовнішніх провайдерів.
- Токени: access RS256 на 30 хв, refresh — хеш у БД, ротація при кожному оновленні.
- **Публічні посилання на маршрут** — єдиний анонімний вхід у trip-service, винесений
  в окремий шлях `/api/public/**` і окремий роут gateway: межа «тільки власник» /
  «будь-хто з посиланням» видно прямо в URL, а не в коді контролера. Гість отримує
  вузький DTO без id, власника й побажань; відкликання м'яке (`revoked_at`), і
  частковий unique-індекс гарантує не більше одного активного посилання на подорож.
- **Фільтри планування необов'язкові** за замовчуванням: темп, інтереси, радіус і
  початок дня їдуть у події `trip.plan.requested` як опційні поля, а planner має
  дефолт на кожне — події зі старою схемою читаються без змін.
- **i18n без текстів у бекенді**: сервіси віддають коди (`COOL_RAINY`, `AUTUMN`),
  переклад живе у фронті. Бекенд не знає мови користувача й не має її вгадувати.
