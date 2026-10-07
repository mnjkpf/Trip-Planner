package com.waylo.trip.web;

import com.waylo.trip.dto.BudgetRequest;
import com.waylo.trip.dto.BudgetResponse;
import com.waylo.trip.dto.ExpenseRequest;
import com.waylo.trip.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Бюджет подорожі. Усі методи, що змінюють дані, повертають ЦІЛЕ зведення —
 * так само, як редагування маршруту повертає весь маршрут: клієнту не треба
 * здогадуватись, що саме перерахувалось після додавання витрати.
 */
@RestController
@RequestMapping("/api/trips/{id}")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping("/budget")
    public BudgetResponse budget(@RequestHeader("X-User-Id") UUID userId,
                                 @PathVariable UUID id) {
        return budgetService.get(userId, id);
    }

    @PutMapping("/budget")
    public BudgetResponse setBudget(@RequestHeader("X-User-Id") UUID userId,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody BudgetRequest req) {
        return budgetService.setBudget(userId, id, req);
    }

    @PostMapping("/expenses")
    public BudgetResponse addExpense(@RequestHeader("X-User-Id") UUID userId,
                                     @PathVariable UUID id,
                                     @Valid @RequestBody ExpenseRequest req) {
        return budgetService.addExpense(userId, id, req);
    }

    @PutMapping("/expenses/{expenseId}")
    public BudgetResponse updateExpense(@RequestHeader("X-User-Id") UUID userId,
                                        @PathVariable UUID id,
                                        @PathVariable UUID expenseId,
                                        @Valid @RequestBody ExpenseRequest req) {
        return budgetService.updateExpense(userId, id, expenseId, req);
    }

    @DeleteMapping("/expenses/{expenseId}")
    public BudgetResponse deleteExpense(@RequestHeader("X-User-Id") UUID userId,
                                        @PathVariable UUID id,
                                        @PathVariable UUID expenseId) {
        return budgetService.deleteExpense(userId, id, expenseId);
    }
}
