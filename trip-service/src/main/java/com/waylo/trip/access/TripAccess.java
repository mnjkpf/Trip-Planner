package com.waylo.trip.access;

import com.waylo.trip.domain.Trip;
import com.waylo.trip.domain.TripMember;
import com.waylo.trip.domain.TripRole;
import com.waylo.trip.error.ApiExceptions.TripForbiddenException;
import com.waylo.trip.error.ApiExceptions.TripNotFoundException;
import com.waylo.trip.repository.TripMemberRepository;
import com.waylo.trip.repository.TripRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Єдина точка перевірки доступу до подорожі. Раніше кожен сервіс робив
 * findByIdAndUserId і тим самим зашивав правило «доступ = ти автор». Тепер
 * правило одне й лежить тут, а сервіси лише кажуть, яка роль їм потрібна.
 *
 * Коди відповіді розведені навмисно:
 *   не учасник          → 404, щоб не підтверджувати існування чужої подорожі;
 *   учасник, але слабша роль → 403, бо приховувати від нього подорож безглуздо.
 */
@Component
public class TripAccess {

    private final TripRepository tripRepository;
    private final TripMemberRepository memberRepository;

    public TripAccess(TripRepository tripRepository, TripMemberRepository memberRepository) {
        this.tripRepository = tripRepository;
        this.memberRepository = memberRepository;
    }

    public Trip require(UUID userId, UUID tripId, TripRole minimum) {
        TripMember member = memberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripNotFoundException("Подорож не знайдено"));
        if (!member.getRole().isAtLeast(minimum)) {
            throw new TripForbiddenException("Недостатньо прав для цієї дії");
        }
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Подорож не знайдено"));
    }

    /** Роль користувача в подорожі; кидає 404, якщо він не учасник. */
    public TripRole roleOf(UUID userId, UUID tripId) {
        return memberRepository.findByTripIdAndUserId(tripId, userId)
                .map(TripMember::getRole)
                .orElseThrow(() -> new TripNotFoundException("Подорож не знайдено"));
    }
}
