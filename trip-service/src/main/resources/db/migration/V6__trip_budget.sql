-- Бюджет подорожі: план + фактичні витрати.
--
-- NUMERIC(12,2), а не DOUBLE PRECISION: гроші в плаваючій комі дають класичні
-- 0.1 + 0.2 = 0.30000000000000004, і підсумок за подорож поступово розʼїжджається.
-- 12 знаків вистачає на будь-яку відпустку в будь-якій валюті.
ALTER TABLE trips ADD COLUMN IF NOT EXISTS budget_amount   NUMERIC(12,2);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS budget_currency VARCHAR(3);

CREATE TABLE trip_expenses (
    id         UUID          PRIMARY KEY,
    trip_id    UUID          NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    category   VARCHAR(20)   NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    amount     NUMERIC(12,2) NOT NULL,
    currency   VARCHAR(3)    NOT NULL,
    -- День витрати: null = «на всю подорож» (переліт, готель цілком).
    spent_on   DATE,
    note       VARCHAR(500),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_trip_expenses_amount   CHECK (amount >= 0),
    CONSTRAINT ck_trip_expenses_category CHECK (category IN
        ('FLIGHT','HOTEL','FOOD','TRANSPORT','ACTIVITY','SHOPPING','OTHER'))
);

-- Вибірка завжди «витрати однієї подорожі по датах» — індекс точно під неї.
CREATE INDEX ix_trip_expenses_trip ON trip_expenses (trip_id, spent_on NULLS FIRST, created_at);
