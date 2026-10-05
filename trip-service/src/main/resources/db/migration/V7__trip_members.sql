-- Спільні подорожі: доступ визначає членство, а не лише trips.user_id.
--
-- trips.user_id лишається — це автор подорожі (і власник за замовчуванням).
-- Список доступу живе окремо, щоб ролей могло бути багато, а запит «усі мої
-- подорожі» був одним join'ом, а не двома гілками.
CREATE TABLE trip_members (
    id         UUID         PRIMARY KEY,
    trip_id    UUID         NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    -- FK на users немає навмисно: вони в іншій базі (той самий принцип, що й trips.user_id).
    user_id    UUID         NOT NULL,
    -- Пошту дублюємо тут, щоб показати список учасників без походу в user-service
    -- на кожен рендер. Вона міняється рідко, а розсинхрон не критичний для UI.
    email      VARCHAR(320) NOT NULL,
    role       VARCHAR(10)  NOT NULL,
    invited_by UUID         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_trip_members_user UNIQUE (trip_id, user_id),
    CONSTRAINT ck_trip_members_role CHECK (role IN ('OWNER','EDITOR','VIEWER'))
);

CREATE INDEX ix_trip_members_user ON trip_members (user_id);

-- Backfill: кожна наявна подорож отримує власника. Без цього після міграції
-- користувачі втратили б доступ до власних подорожей — список членства порожній.
-- Пошти тут ще немає (вона в user-service), тому ставимо заглушку: сервіс
-- підміняє її справжньою при першому ж відкритті списку учасників.
INSERT INTO trip_members (id, trip_id, user_id, email, role, invited_by)
SELECT gen_random_uuid(), t.id, t.user_id, '', 'OWNER', t.user_id
FROM trips t;
