-- Збережена мова UI (ISO 639-1, напр. "en", "uk"); null → клієнт бере з браузера/localStorage.
ALTER TABLE users ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(5);
