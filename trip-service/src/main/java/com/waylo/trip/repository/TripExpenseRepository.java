package com.waylo.trip.repository;

import com.waylo.trip.domain.TripExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripExpenseRepository extends JpaRepository<TripExpense, UUID> {

    /**
     * Витрати подорожі: спершу «на всю подорож» (spentOn = null), далі за датами.
     * Запит явний, бо похідний метод дав би просто ORDER BY spent_on ASC, а в
     * Postgres це кладе NULL у КІНЕЦЬ — переліт і готель опинились би після
     * щоденних витрат. NULLS FIRST збігається і з індексом із міграції V6.
     */
    @Query("select e from TripExpense e where e.tripId = :tripId "
            + "order by e.spentOn asc nulls first, e.createdAt asc")
    List<TripExpense> findForTrip(@Param("tripId") UUID tripId);

    /** Пошук із перевіркою подорожі — щоб не видалити чужу витрату за вгаданим id. */
    Optional<TripExpense> findByIdAndTripId(UUID id, UUID tripId);
}
