-- Переводимо place_id у VARCHAR, щоб дозволити «власні» ID вигляду "custom:abc123"
-- (додані користувачем через посилання Google Maps). Існуючі UUID-значення
-- конвертуються у їх текстове представлення автоматично.

ALTER TABLE wishlist_items
    ALTER COLUMN place_id TYPE VARCHAR(100) USING place_id::text;

ALTER TABLE itinerary_items
    ALTER COLUMN place_id TYPE VARCHAR(100) USING place_id::text;
