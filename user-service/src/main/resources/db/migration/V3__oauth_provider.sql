-- Вхід через Google: у таких акаунтів немає локального пароля, тому
-- password_hash робимо nullable. oauth_provider показує, звідки акаунт:
-- NULL = локальна реєстрація, 'google' = Google Sign-In.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS oauth_provider VARCHAR(20);
