-- trip-service: джерело правди для користувацьких даних.

CREATE TABLE trips (
    id                   UUID         PRIMARY KEY,
    -- user_id приходить з JWT (claim sub → заголовок X-User-Id від gateway).
    -- Зовнішнього ключа немає навмисно: users живуть в іншій базі.
    user_id              UUID         NOT NULL,
    title                VARCHAR(200) NOT NULL,

    destination_name     VARCHAR(200) NOT NULL,
    destination_country  VARCHAR(2),
    destination_lat      DOUBLE PRECISION NOT NULL,
    destination_lon      DOUBLE PRECISION NOT NULL,

    start_date           DATE         NOT NULL,
    end_date             DATE         NOT NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_trips_status CHECK (status IN ('DRAFT','PLANNING','PLANNED','ARCHIVED')),
    CONSTRAINT ck_trips_dates  CHECK (end_date >= start_date)
);

CREATE INDEX ix_trips_user ON trips (user_id, start_date DESC);

CREATE TABLE trip_days (
    id         UUID    PRIMARY KEY,
    trip_id    UUID    NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    day_date   DATE    NOT NULL,
    day_index  INTEGER NOT NULL,
    CONSTRAINT ux_trip_days UNIQUE (trip_id, day_date)
);

CREATE TABLE itinerary_items (
    id             UUID    PRIMARY KEY,
    trip_day_id    UUID    NOT NULL REFERENCES trip_days (id) ON DELETE CASCADE,

    -- Внутрішній id місця з place-service, ніколи не id провайдера.
    place_id       UUID    NOT NULL,

    -- Денормалізований снапшот: щоб намалювати список маршруту не потрібен
    -- виклик у place-service. Оновлюється консюмером place.enriched (далі).
    place_name        VARCHAR(300) NOT NULL,
    place_category    VARCHAR(64),
    place_lat         DOUBLE PRECISION NOT NULL,
    place_lon         DOUBLE PRECISION NOT NULL,
    place_image_url   VARCHAR(1000),
    snapshot_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    order_index               INTEGER NOT NULL,
    planned_start             TIME,
    planned_end               TIME,
    dwell_minutes             INTEGER NOT NULL DEFAULT 60,
    travel_mode_from_prev     VARCHAR(16),
    travel_minutes_from_prev  INTEGER,

    -- Користувач «прибив» пункт до часу — оптимізатор його не рухає.
    locked         BOOLEAN NOT NULL DEFAULT FALSE,
    note           TEXT,

    CONSTRAINT ux_itinerary_order UNIQUE (trip_day_id, order_index) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX ix_itinerary_items_day   ON itinerary_items (trip_day_id);
CREATE INDEX ix_itinerary_items_place ON itinerary_items (place_id);

CREATE TABLE wishlist_items (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL,
    place_id    UUID        NOT NULL,
    place_name  VARCHAR(300) NOT NULL,
    place_lat   DOUBLE PRECISION,
    place_lon   DOUBLE PRECISION,
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_wishlist UNIQUE (user_id, place_id)
);

CREATE INDEX ix_wishlist_user ON wishlist_items (user_id, created_at DESC);

-- Задача планування. Клієнт отримує її id одразу (202 Accepted) і питає
-- статус, поки planner працює.
CREATE TABLE plan_jobs (
    id            UUID        PRIMARY KEY,
    trip_id       UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ,
    CONSTRAINT ck_plan_jobs_status CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED'))
);

CREATE INDEX ix_plan_jobs_trip ON plan_jobs (trip_id, requested_at DESC);

-- ---------------------------------------------------------------
-- Outbox. Розв'язує dual write: подія пишеться в ТУ САМУ транзакцію, що й
-- зміна даних. Окремий publisher вичитує таблицю і відправляє в Kafka.
-- Якщо процес упаде між комітом і відправкою — подія лишиться тут і піде
-- наступним циклом.
-- ---------------------------------------------------------------
CREATE TABLE outbox (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    headers        JSONB,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     TEXT
);

-- Частковий індекс: publisher шукає лише неопубліковані. Індекс не росте
-- разом з історією відправлених подій.
CREATE INDEX ix_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
