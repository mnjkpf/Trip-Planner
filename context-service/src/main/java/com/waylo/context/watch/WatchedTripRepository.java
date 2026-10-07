package com.waylo.context.watch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WatchedTripRepository extends JpaRepository<WatchedTrip, UUID> {

    /** Подорожі, що вже тривають або почнуться не пізніше за lastStart. */
    List<WatchedTrip> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate lastStart, LocalDate today);

    /**
     * Точкове оновлення лише службових полів. НЕ save(entity): job тримає знімок,
     * прочитаний раніше, і save() міг би затерти свіжіший маршрут, який тим часом
     * записав Kafka-лісенер.
     */
    @Modifying
    @Transactional
    @Query("update WatchedTrip w set w.lastFingerprint = :fp, w.lastCheckedAt = :at where w.tripId = :id")
    int markChecked(@Param("id") UUID id, @Param("fp") String fingerprint, @Param("at") Instant at);

    /** Прибирання: подорожі, що вже закінчились, наглядати більше не треба. */
    @Modifying
    @Transactional
    @Query("delete from WatchedTrip w where w.endDate < :date")
    int deleteFinishedBefore(@Param("date") LocalDate date);
}
