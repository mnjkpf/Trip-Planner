-- Погодні попередження від context-service (топік trip.weather.alert).
--
-- Це ПОРАДИ, а не дії: маршрут ніхто автоматично не переставляє. Таблиця
-- зберігає лише останню версію поради для кожного дня подорожі — нова подія
-- замінює попередню.
CREATE TABLE trip_weather_alerts (
    id                    UUID             PRIMARY KEY,
    trip_id               UUID             NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    day_date              DATE             NOT NULL,
    day_index             INT              NOT NULL,
    level                 VARCHAR(16)      NOT NULL,
    precipitation_mm      DOUBLE PRECISION NOT NULL,
    -- Назви місць «під відкритим небом» через перенос рядка — лише для тексту в UI.
    outdoor_places        TEXT             NOT NULL DEFAULT '',
    -- День, з яким варто подумати про обмін; NULL — сухішого дня немає.
    swap_date             DATE,
    swap_day_index        INT,
    swap_precipitation_mm DOUBLE PRECISION,
    generated_at          TIMESTAMPTZ      NOT NULL,

    CONSTRAINT uq_trip_weather_alerts_day UNIQUE (trip_id, day_date),
    CONSTRAINT ck_trip_weather_alerts_level CHECK (level IN ('RAIN','HEAVY_RAIN'))
);

-- «Зрозуміло, сховати» — особисто для кожного учасника: один закрив попередження,
-- інші все одно його побачать. Якщо порада для дня зміниться (інший день для
-- обміну), рядок попередження створюється заново і каскадом забирає ці відмітки.
CREATE TABLE trip_weather_alert_dismissals (
    id         UUID        PRIMARY KEY,
    alert_id   UUID        NOT NULL REFERENCES trip_weather_alerts (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_weather_alert_dismissal UNIQUE (alert_id, user_id)
);
