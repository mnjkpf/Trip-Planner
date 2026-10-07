-- Публічне посилання на маршрут: «будь-хто з посиланням бачить, але не редагує».
--
-- Токен зберігаємо У ВІДКРИТОМУ ВИГЛЯДІ, на відміну від refresh-токенів. Причина:
-- посилання треба показувати власнику скільки завгодно разів («де моє посилання?»),
-- а з хеша вихідний токен не дістати. Компроміс свідомий: токен дає ЛИШЕ читання
-- маршруту, який користувач сам вирішив опублікувати, і будь-якої миті відкликається.
CREATE TABLE trip_shares (
    id          UUID         PRIMARY KEY,
    trip_id     UUID         NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    token       VARCHAR(64)  NOT NULL UNIQUE,
    created_by  UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ
);

-- Активне посилання на подорож максимум одне: повторний POST /share має
-- повертати те саме, а не плодити нові. Частковий unique-індекс це гарантує
-- на рівні БД, а не лише в коді сервісу.
CREATE UNIQUE INDEX ux_trip_shares_active ON trip_shares (trip_id) WHERE revoked_at IS NULL;
