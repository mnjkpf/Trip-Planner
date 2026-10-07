package com.waylo.trip.repository;

import com.waylo.trip.domain.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripMemberRepository extends JpaRepository<TripMember, UUID> {

    Optional<TripMember> findByTripIdAndUserId(UUID tripId, UUID userId);

    /** Усе членство користувача — щоб у списку подорожей не ходити по ролі на кожну. */
    List<TripMember> findByUserId(UUID userId);

    /**
     * Сортування НЕ в запиті: роль зберігається рядком, тож ORDER BY role дав би
     * алфавіт (EDITOR, OWNER, VIEWER) замість ієрархії. Порядок задає сервіс
     * по ordinal() енума.
     */
    List<TripMember> findByTripId(UUID tripId);

    Optional<TripMember> findByIdAndTripId(UUID id, UUID tripId);

    boolean existsByTripIdAndUserId(UUID tripId, UUID userId);
}
