package com.waylo.trip.service;

import com.waylo.trip.access.TripAccess;
import com.waylo.trip.client.UserClient;
import com.waylo.trip.client.UserLookup;
import com.waylo.trip.domain.Trip;
import com.waylo.trip.domain.TripMember;
import com.waylo.trip.domain.TripRole;
import com.waylo.trip.dto.ChangeRoleRequest;
import com.waylo.trip.dto.InviteMemberRequest;
import com.waylo.trip.dto.MemberResponse;
import com.waylo.trip.error.ApiExceptions.TripConflictException;
import com.waylo.trip.error.ApiExceptions.TripForbiddenException;
import com.waylo.trip.error.ApiExceptions.TripNotFoundException;
import com.waylo.trip.repository.TripMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Учасники подорожі. Запрошуємо лише вже зареєстрованих — інакше довелося б
 * тримати «відкладені запрошення» й активувати їх при реєстрації, а це окрема
 * машина станів із протермінуванням і повторними листами. Для пет-проєкту
 * чесніше сказати «спершу хай зареєструється», ніж зробити це наполовину.
 */
@Service
public class MemberService {

    private final TripMemberRepository memberRepository;
    private final TripAccess access;
    private final UserClient userClient;

    public MemberService(TripMemberRepository memberRepository,
                         TripAccess access,
                         UserClient userClient) {
        this.memberRepository = memberRepository;
        this.access = access;
        this.userClient = userClient;
    }

    /** Список бачить будь-який учасник: кому ще відкрито твою подорож — не таємниця. */
    @Transactional
    public List<MemberResponse> list(UUID userId, String userEmail, UUID tripId) {
        access.require(userId, tripId, TripRole.VIEWER);
        List<TripMember> members = sorted(tripId);
        backfillOwnEmail(members, userId, userEmail);
        return members.stream().map(m -> toResponse(m, userId)).toList();
    }

    /**
     * Подорожі, створені до появи учасників, отримали рядок власника без пошти
     * (у міграції її взяти було нізвідки — вона в user-service). Дописуємо її,
     * коли власник сам відкриває список: пошта приходить у X-User-Email із
     * токена, тож зайвого виклику не треба.
     */
    private void backfillOwnEmail(List<TripMember> members, UUID userId, String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return;
        }
        members.stream()
                .filter(m -> m.getUserId().equals(userId) && (m.getEmail() == null || m.getEmail().isBlank()))
                .findFirst()
                .ifPresent(m -> m.setEmail(userEmail));
    }

    @Transactional
    public List<MemberResponse> invite(UUID userId, UUID tripId, InviteMemberRequest req) {
        Trip trip = access.require(userId, tripId, TripRole.OWNER);

        UserLookup invited = userClient.findByEmail(req.email())
                .orElseThrow(() -> new TripNotFoundException(
                        "Такої пошти немає в Waylo — хай спершу зареєструється"));

        if (invited.id().equals(trip.getUserId())) {
            throw new TripConflictException("Це власник подорожі");
        }
        if (memberRepository.existsByTripIdAndUserId(tripId, invited.id())) {
            throw new TripConflictException("Цей користувач уже має доступ");
        }

        memberRepository.saveAndFlush(TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .userId(invited.id())
                .email(invited.email())
                .role(TripRole.valueOf(req.role()))
                .invitedBy(userId)
                .build());
        return list(userId, null, tripId);
    }

    @Transactional
    public List<MemberResponse> changeRole(UUID userId, UUID tripId, UUID memberId, ChangeRoleRequest req) {
        access.require(userId, tripId, TripRole.OWNER);
        TripMember member = requireMember(tripId, memberId);
        if (member.getRole() == TripRole.OWNER) {
            throw new TripForbiddenException("Роль власника змінити не можна");
        }
        member.setRole(TripRole.valueOf(req.role()));
        return list(userId, null, tripId);
    }

    /**
     * Прибрати учасника може власник — або сам учасник, якщо вирішив вийти.
     * Власника видалити не можна взагалі: подорож лишилась би без господаря.
     */
    @Transactional
    public void remove(UUID userId, UUID tripId, UUID memberId) {
        TripMember member = requireMember(tripId, memberId);
        if (member.getRole() == TripRole.OWNER) {
            throw new TripForbiddenException("Власника прибрати не можна");
        }
        boolean self = member.getUserId().equals(userId);
        if (!self) {
            access.require(userId, tripId, TripRole.OWNER);
        } else {
            access.require(userId, tripId, TripRole.VIEWER);
        }
        memberRepository.delete(member);
    }

    private TripMember requireMember(UUID tripId, UUID memberId) {
        return memberRepository.findByIdAndTripId(memberId, tripId)
                .orElseThrow(() -> new TripNotFoundException("Учасника не знайдено"));
    }

    /**
     * Власник → редактори → глядачі. Сортуємо в памʼяті, бо роль лежить рядком
     * і ORDER BY у БД дав би алфавіт замість ієрархії.
     */
    private List<TripMember> sorted(UUID tripId) {
        return memberRepository.findByTripId(tripId).stream()
                .sorted(Comparator.comparingInt((TripMember m) -> m.getRole().ordinal())
                        .thenComparing(TripMember::getCreatedAt))
                .toList();
    }

    private static MemberResponse toResponse(TripMember m, UUID viewerId) {
        return new MemberResponse(m.getId(), m.getUserId(), m.getEmail(),
                m.getRole(), m.getUserId().equals(viewerId), m.getCreatedAt());
    }
}
