-- user-service: власники ідентичності. Жоден інший сервіс сюди не пише.

CREATE TABLE users (
    id             UUID         PRIMARY KEY,
    email          VARCHAR(320) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    display_name   VARCHAR(120),
    role           VARCHAR(32)  NOT NULL DEFAULT 'USER',
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Пошта нечутлива до регістру: унікальність по lower(email),
-- інакше Roman@ і roman@ зареєструються обидва.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

-- Refresh-токени зберігаємо ЯК ХЕШ. Якщо базу зіллють — самі токени
-- з неї не дістануть.
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,     -- SHA-256 hex
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);
