package com.waylo.place.repository;

import com.waylo.place.domain.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

    /**
     * Місця в радіусі (метри) від точки, опційно за категорією, відсортовані за
     * відстанню. Колонка location — geometry, тож кастуємо до geography, щоб
     * ST_DWithin і ST_Distance рахували в метрах, а не в градусах.
     * GiST-індекс на location робить це швидким.
     */
    @Query(value = """
            SELECT * FROM places p
            WHERE (CAST(:category AS text) IS NULL OR p.category = CAST(:category AS text))
              AND ST_DWithin(
                    p.location::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                    :radiusMeters)
            ORDER BY p.location::geography <->
                     ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
            LIMIT :limit
            """, nativeQuery = true)
    List<Place> findNearby(@Param("lat") double lat,
                           @Param("lon") double lon,
                           @Param("radiusMeters") int radiusMeters,
                           @Param("category") String category,
                           @Param("limit") int limit);
}
