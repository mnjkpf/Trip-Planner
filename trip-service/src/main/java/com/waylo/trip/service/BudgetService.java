package com.waylo.trip.service;

import com.waylo.trip.domain.ExpenseCategory;
import com.waylo.trip.domain.Trip;
import com.waylo.trip.domain.TripExpense;
import com.waylo.trip.dto.BudgetRequest;
import com.waylo.trip.dto.BudgetResponse;
import com.waylo.trip.dto.ExpenseRequest;
import com.waylo.trip.dto.ExpenseResponse;
import com.waylo.trip.error.ApiExceptions.TripNotFoundException;
import com.waylo.trip.repository.TripExpenseRepository;
import com.waylo.trip.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Бюджет подорожі: план і фактичні витрати.
 *
 * Жодної конвертації валют — курсів у системі немає, а складати євро з гривнями
 * «приблизно» гірше, ніж не складати взагалі. Підсумки рахуються окремо по
 * кожній валюті, а залишок — тільки для валюти плану.
 */
@Service
public class BudgetService {

    private final TripRepository tripRepository;
    private final TripExpenseRepository expenseRepository;

    public BudgetService(TripRepository tripRepository, TripExpenseRepository expenseRepository) {
        this.tripRepository = tripRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(UUID userId, UUID tripId) {
        return summary(requireOwned(userId, tripId));
    }

    /** Плановий бюджет. amount == null стирає план, валюта тоді теж не потрібна. */
    @Transactional
    public BudgetResponse setBudget(UUID userId, UUID tripId, BudgetRequest req) {
        Trip trip = requireOwned(userId, tripId);
        if (req.amount() == null) {
            trip.setBudgetAmount(null);
            trip.setBudgetCurrency(null);
        } else {
            trip.setBudgetAmount(req.amount());
            trip.setBudgetCurrency(normalizeCurrency(
                    req.currency() != null ? req.currency() : trip.getBudgetCurrency()));
        }
        return summary(trip);
    }

    @Transactional
    public BudgetResponse addExpense(UUID userId, UUID tripId, ExpenseRequest req) {
        Trip trip = requireOwned(userId, tripId);
        String currency = normalizeCurrency(req.currency());

        expenseRepository.saveAndFlush(TripExpense.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .category(ExpenseCategory.valueOf(req.category()))
                .title(req.title().trim())
                .amount(req.amount())
                .currency(currency)
                .spentOn(req.spentOn())
                .note(blankToNull(req.note()))
                .build());

        // Перша витрата задає валюту плану, якщо її ще не вибрали: інакше
        // «залишок» лишався б порожнім, поки користувач сам не здогадається.
        if (trip.getBudgetCurrency() == null) {
            trip.setBudgetCurrency(currency);
        }
        return summary(trip);
    }

    @Transactional
    public BudgetResponse updateExpense(UUID userId, UUID tripId, UUID expenseId, ExpenseRequest req) {
        Trip trip = requireOwned(userId, tripId);
        TripExpense expense = expenseRepository.findByIdAndTripId(expenseId, tripId)
                .orElseThrow(() -> new TripNotFoundException("Витрату не знайдено"));

        expense.setCategory(ExpenseCategory.valueOf(req.category()));
        expense.setTitle(req.title().trim());
        expense.setAmount(req.amount());
        expense.setCurrency(normalizeCurrency(req.currency()));
        expense.setSpentOn(req.spentOn());
        expense.setNote(blankToNull(req.note()));
        return summary(trip);
    }

    @Transactional
    public BudgetResponse deleteExpense(UUID userId, UUID tripId, UUID expenseId) {
        Trip trip = requireOwned(userId, tripId);
        expenseRepository.findByIdAndTripId(expenseId, tripId)
                .ifPresent(expenseRepository::delete);
        expenseRepository.flush();
        return summary(trip);
    }

    private BudgetResponse summary(Trip trip) {
        List<TripExpense> expenses = expenseRepository.findForTrip(trip.getId());

        Map<String, BigDecimal> byCurrency = new LinkedHashMap<>();
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();   // ключ "CATEGORY|CUR"
        for (TripExpense e : expenses) {
            byCurrency.merge(e.getCurrency(), e.getAmount(), BigDecimal::add);
            byCategory.merge(e.getCategory().name() + "|" + e.getCurrency(), e.getAmount(), BigDecimal::add);
        }

        String planned = trip.getBudgetCurrency();
        BigDecimal spent = planned == null ? null : byCurrency.getOrDefault(planned, BigDecimal.ZERO);
        BigDecimal remaining = (trip.getBudgetAmount() == null || spent == null)
                ? null
                : trip.getBudgetAmount().subtract(spent);

        List<BudgetResponse.CurrencyTotal> totals = new ArrayList<>();
        byCurrency.forEach((cur, amount) -> totals.add(new BudgetResponse.CurrencyTotal(cur, amount)));

        List<BudgetResponse.CategoryTotal> categories = new ArrayList<>();
        byCategory.forEach((key, amount) -> {
            String[] parts = key.split("\\|", 2);
            categories.add(new BudgetResponse.CategoryTotal(parts[0], parts[1], amount));
        });

        return new BudgetResponse(
                trip.getBudgetAmount(), planned, spent, remaining,
                totals, categories, expenses.stream().map(BudgetService::toResponse).toList());
    }

    private static ExpenseResponse toResponse(TripExpense e) {
        return new ExpenseResponse(
                e.getId(), e.getCategory().name(), e.getTitle(), e.getAmount(),
                e.getCurrency(), e.getSpentOn(), e.getNote(), e.getCreatedAt());
    }

    private Trip requireOwned(UUID userId, UUID tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripNotFoundException("Подорож не знайдено"));
    }

    private static String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
