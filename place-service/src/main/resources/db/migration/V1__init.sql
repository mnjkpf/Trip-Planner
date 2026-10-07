-- place-service: канонічний каталог місць.
-- Розширення створюємо тут через IF NOT EXISTS — так міграція працює і на
-- локальному placedb (де їх уже створив init-скрипт compose), і в Testcontainers.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE places (
    id               UUID          PRIMARY KEY,
    name             VARCHAR(300)  NOT NULL,
    category         VARCHAR(64)   NOT NULL,
    description      TEXT,

    -- geometry(Point,4326), а не geography: Hibernate мапить JTS Point саме
    -- на geometry, тож ddl-auto: validate проходить чисто. Метричні відстані
    -- рахуємо, кастуючи до geography прямо в запиті (location::geography).
    location         geometry(Point, 4326) NOT NULL,

    country_code     VARCHAR(2),
    city             VARCHAR(160),
    address          VARCHAR(500),
    opening_hours    VARCHAR(500),
    website          VARCHAR(500),
    phone            VARCHAR(64),
    wikidata_id      VARCHAR(32),
    image_url        VARCHAR(1000),

    source_provider  VARCHAR(32)   NOT NULL,

    fetched_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    enriched_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Головний індекс проекту: без нього ST_DWithin робить seq scan.
CREATE INDEX ix_places_location    ON places USING GIST (location);
CREATE INDEX ix_places_category    ON places (category);
CREATE INDEX ix_places_country_city ON places (country_code, city);
CREATE INDEX ix_places_fetched_at  ON places (fetched_at);
-- Пошук за назвою з опечатками
CREATE INDEX ix_places_name_trgm   ON places USING GIN (name gin_trgm_ops);

-- Один і той самий обʼєкт приходить з Overpass, Geoapify і OpenTripMap з
-- різними id. Вішліст користувача посилатиметься на places.id, тож заміна
-- провайдера нічого не ламає.
CREATE TABLE place_external_refs (
    id           UUID         PRIMARY KEY,
    place_id     UUID         NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    provider     VARCHAR(32)  NOT NULL,
    external_id  VARCHAR(200) NOT NULL,
    raw_payload  JSONB,
    fetched_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_place_external UNIQUE (provider, external_id)
);

CREATE INDEX ix_place_external_refs_place ON place_external_refs (place_id);
