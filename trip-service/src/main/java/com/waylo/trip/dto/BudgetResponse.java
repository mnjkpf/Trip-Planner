package com.waylo.trip.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Зведення бюджету.
 *
 * Валюти НЕ конвертуємо: курсів у системі немає, а мовчки складати євро з
 * гривнями — найгірший варіант із можливих. Тому підсумки йдуть окремо по
 * кожній валюті (totals), а spent/remaining рахуються лише для валюти плану.
 */
public record BudgetResponse(
        BigDecimal plannedAmount,
        String plannedCurrency,
        BigDecimal spent,
        BigDecimal remaining,
        List<CurrencyTotal> totals,
        List<CategoryTotal> byCategory,
        List<ExpenseResponse> expenses
) {
    public record CurrencyTotal(String currency, BigDecimal amount) {}

    public record CategoryTotal(String category, String currency, BigDecimal amount) {}
}
