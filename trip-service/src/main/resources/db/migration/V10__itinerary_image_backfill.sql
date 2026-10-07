-- Фото місця тепер приходить разом із маршрутом (planner бере його з place-service,
-- той — з Wikipedia). Для вже спланованих подорожей колонка порожня, тож трип-сервіс
-- дозаповнює її у фоні.
--
-- Позначка «вже питали» потрібна саме тому, що ВІДСУТНІСТЬ фото — теж відповідь:
-- без неї job щоразу перепитував би ті самі місця, у яких фото просто немає.
ALTER TABLE itinerary_items ADD COLUMN image_checked_at TIMESTAMPTZ;

-- Частковий індекс: job шукає рівно «ще не питали», і їх з часом лишаються одиниці.
CREATE INDEX ix_itinerary_items_image_pending
    ON itinerary_items (snapshot_at DESC)
    WHERE image_checked_at IS NULL;
