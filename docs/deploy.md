# Деплой Waylo

Бекенд цілком живе на одній віртуалці як `docker compose`, фронт — на Vercel.
Такий поділ не випадковий: Kafka, Postgres і обʼєктне сховище потребують машини,
яка працює постійно, а статика чудово роздається з CDN безкоштовно.

Орієнтовна вартість: **€7–9/міс** за сервер плюс ~$12/рік за домен.
Vercel, GHCR і Let's Encrypt — безкоштовні.

---

## 1. Сервер

Hetzner Cloud, тип **CAX21** (4 ядра ARM, 8 ГБ, 80 ГБ диска), Ubuntu 24.04.
ARM тут не екзотика, а економія: за ті самі гроші вдвічі більше ресурсів, а всі
наші образи мультиархітектурні.

Памʼять розподілена так (див. `mem_limit` у компоузі): вісім JVM-сервісів разом
беруть ~3.2 ГБ, Kafka — 1 ГБ, Postgres — 768 МБ, решта по дрібному. Виходить
близько 5.75 ГБ із восьми, решта лишається під сторінковий кеш і сплески.

```bash
# на свіжому сервері
apt update && apt install -y docker.io docker-compose-v2 git
systemctl enable --now docker

# фаєрвол: назовні тільки 22, 80 і 443
ufw allow OpenSSH && ufw allow 80 && ufw allow 443 && ufw --force enable
```

## 2. Домен

Купи будь-який (Namecheap, Porkbun — близько $12/рік) або візьми безкоштовний
на DuckDNS. Потрібен один A-запис:

```
api.твій-домен   A   <IP сервера>
```

**Зроби це до першого запуску.** Caddy отримує сертифікат Let's Encrypt у момент
старту, і якщо домен ще не резолвиться — отримає помилку й піде на повтор.

## 3. Код і секрети

```bash
git clone https://github.com/mnjkpf/Trip-Planner.git waylo && cd waylo

# Ключі для підпису JWT — генеруються на сервері й нікуди не комітяться
mkdir -p infra/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out infra/keys/jwt-private.pem
openssl rsa -pubout -in infra/keys/jwt-private.pem -out infra/keys/jwt-public.pem

cp deploy/.env.example deploy/.env
nano deploy/.env          # домен, пошта, пароль Postgres, ключі провайдерів
```

Пароль Postgres зручно згенерувати так: `openssl rand -base64 24`.

## 4. Запуск

Образи збирає GitHub Actions під `linux/arm64` і кладе в GHCR — сервер нічого не
компілює, лише завантажує.

```bash
cd ~/waylo
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env pull
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d
docker compose -f deploy/docker-compose.prod.yml ps
```

Перший старт триває хвилини три: Postgres створює чотири бази, Flyway накочує
міграції, сервіси чекають одне на одного за healthcheck. Перевірка:

```bash
curl https://api.твій-домен/actuator/health
```

Якщо пакети в GHCR приватні, спершу `docker login ghcr.io` з токеном, у якого є
право `read:packages`. Простіше зробити їх публічними — у налаштуваннях пакета
на GitHub.

## 5. Фронт на Vercel

1. Імпортуй репозиторій, **Root Directory** постав `frontend/web`.
2. У `frontend/web/vercel.json` заміни `API_DOMAIN_PLACEHOLDER` на свій домен
   (`api.твій-домен`) і закоміть.
3. Додай змінні оточення проєкту: `GOOGLE_MAPS_API_KEY`, `GOOGLE_MAPS_MAP_ID`,
   `GOOGLE_OAUTH_CLIENT_ID`.

Збирання саме викличе `scripts/write-env.mjs` — той згенерує `environment.ts`,
якого в репозиторії немає навмисно (у ньому ключ Maps).

Запити `/api/*` Vercel проксує на домен сервера своїм rewrite. Завдяки цьому у
фронті лишаються відносні шляхи, у коді не змінюється жодного рядка, і не
виникає CORS — для браузера все одне походження.

## 6. Google OAuth

У Google Cloud Console → Credentials додай до **Authorized JavaScript origins**
адресу з Vercel (`https://твій-проєкт.vercel.app` і власний домен, якщо він є).
Без цього кнопка входу через Google віддасть 403, а звичайний вхід працюватиме.

## 7. Оновлення

Пуш у `main` збирає нові образи. На сервері:

```bash
cd ~/waylo && git pull
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env pull
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d
```

Відкотитись на конкретну версію: постав у `deploy/.env` `WAYLO_TAG=sha-abc1234`
(теги видно на сторінці пакета в GHCR) і повтори `up -d`.

## Спостережуваність

Grafana LGTM вимкнена за замовчуванням — вона просить ще близько двох гігабайтів.
Якщо треба подивитись трейси:

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env --profile obs up -d
```

І прибрати `OTEL_SDK_DISABLED: "true"` у сервісів, трейси яких цікавлять.

## Бекап

Усе важливе — у двох томах: `waylo_pgdata` (бази) і `waylo_objectsdata` (фото).

```bash
docker run --rm -v waylo_pgdata:/data -v $PWD:/backup alpine \
  tar czf /backup/pgdata-$(date +%F).tar.gz -C /data .
```
