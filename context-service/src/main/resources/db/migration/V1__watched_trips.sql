-- Проєкція маршрутів для погодного нагляду (CQRS read model).
--
-- Джерело правди — trip-service. Сюди потрапляє лише останній знімок маршруту
-- з топіка trip.itinerary.snapshot, і рівно в тій формі, яка потрібна job'у:
-- координати, дати й дні з категоріями пунктів. Ходити за цим у trip-service
-- синхронно не треба — нагляд працює, навіть коли trip-service лежить.
CREATE TABLE watched_trips (
    trip_id          UUID             PRIMARY KEY,
    destination_name VARCHAR(255),
    lat              DOUBLE PRECISION NOT NULL,
    lon              DOUBLE PRECISION NOT NULL,
    start_date       DATE             NOT NULL,
    end_date         DATE             NOT NULL,
    -- Час знімка: старіший за збережений ігноруємо (захист від перевпорядкування).
    snapshot_at      TIMESTAMPTZ      NOT NULL,
    -- [{dayIndex, date, places: [{name, category}]}] — читається цілком, тож JSONB, а не таблиці.
    days             JSONB            NOT NULL DEFAULT '[]',
    last_checked_at  TIMESTAMPTZ,
    -- Відбиток останньої відправленої поради: подія йде в Kafka лише коли він змінився.
    last_fingerprint TEXT
);

-- Job щоразу питає «хто їде найближчими днями» — саме по цих двох датах.
CREATE INDEX ix_watched_trips_window ON watched_trips (start_date, end_date);
