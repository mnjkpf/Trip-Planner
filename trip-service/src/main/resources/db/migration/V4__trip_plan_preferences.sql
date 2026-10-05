-- Опційні налаштування планування. Усі nullable: подорожі, створені до фічі,
-- і ті, де користувач нічого не чіпав, плануються з дефолтами планувальника.
ALTER TABLE trips ADD COLUMN IF NOT EXISTS pace            VARCHAR(16);   -- RELAXED|BALANCED|PACKED
ALTER TABLE trips ADD COLUMN IF NOT EXISTS interests       VARCHAR(200);  -- CSV категорій: "PARK,BEACH"
ALTER TABLE trips ADD COLUMN IF NOT EXISTS search_radius_m INT;           -- радіус добору POI, метри
ALTER TABLE trips ADD COLUMN IF NOT EXISTS day_start_time  TIME;          -- о котрій починати день
