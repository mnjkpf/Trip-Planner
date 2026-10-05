package com.waylo.trip.repository;

import com.waylo.trip.domain.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    /**
     * Усі подорожі, де користувач є учасником — свої й ті, куди його запросили.
     * Entity join по TripMember, бо звʼязку в мапінгу навмисно немає: членство
     * живе окремою таблицею й не тягнеться разом із подорожжю.
     */
    @Query("select t from Trip t join TripMember m on m.tripId = t.id "
            + "where m.userId = :userId order by t.startDate desc")
    List<Trip> findForMember(@Param("userId") UUID userId);

}
