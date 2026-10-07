-- Фото подорожі. Байти живуть у media-service (MinIO), тут — лише посилання
-- на них і контекст: до якої подорожі й, за бажанням, до якого місця маршруту.
--
-- Статус UPLOADING -> READY: рядок створюється разом із тікетом на завантаження,
-- а READY ставить подія media.ready. Якщо користувач передумав і файл так і не
-- приїхав, рядок приберe планове прибирання (PhotoService.cleanupStale).
CREATE TABLE trip_photos (
    id                UUID         PRIMARY KEY,
    trip_id           UUID         NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    -- Ідентифікатор у media-service. UNIQUE, бо подія media.ready приходить
    -- саме за ним — і має знаходити рівно один рядок.
    media_id          UUID         NOT NULL UNIQUE,
    -- Прив'язка до пункту маршруту. ON DELETE SET NULL: прибрали місце з плану —
    -- фото лишається в галереї подорожі, а не зникає разом із ним.
    itinerary_item_id UUID         REFERENCES itinerary_items (id) ON DELETE SET NULL,
    -- Назву місця дублюємо: пункт може зникнути, а підпис «Колізей» має лишитись.
    place_name        VARCHAR(255),
    caption           VARCHAR(300),
    status            VARCHAR(16)  NOT NULL,
    content_type      VARCHAR(100),
    bytes             BIGINT,
    width             INT,
    height            INT,
    uploaded_by       UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ready_at          TIMESTAMPTZ,

    CONSTRAINT ck_trip_photos_status CHECK (status IN ('UPLOADING','READY'))
);

CREATE INDEX ix_trip_photos_trip ON trip_photos (trip_id, created_at);
CREATE INDEX ix_trip_photos_item ON trip_photos (itinerary_item_id);
