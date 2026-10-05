package com.waylo.trip;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Бюджет: план, витрати, підсумки. Ключове, що тут перевіряється — різні валюти
 * НЕ складаються в одну купу, а залишок рахується лише для валюти плану.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BudgetApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) {
        return jsonMapper.readValue(json, Map.class);
    }

    private String createTrip(UUID userId) throws Exception {
        String body = """
                {"title":"Dublin trip","destinationName":"Dublin","destinationCountry":"IE",
                 "destinationLat":53.35,"destinationLon":-6.26,
                 "startDate":"2026-10-19","endDate":"2026-10-23"}
                """;
        MvcResult res = mockMvc.perform(post("/api/trips")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return (String) asMap(res.getResponse().getContentAsString()).get("id");
    }

    private Map<String, Object> addExpense(UUID user, String tripId, String json) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/trips/" + tripId + "/expenses")
                        .header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        return asMap(res.getResponse().getContentAsString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void plannedBudget_spentAndRemaining_areComputedForPlannedCurrency() throws Exception {
        UUID user = UUID.randomUUID();
        String tripId = createTrip(user);

        mockMvc.perform(put("/api/trips/" + tripId + "/budget")
                        .header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000.00,\"currency\":\"eur\"}"))
                .andExpect(status().isOk());

        addExpense(user, tripId, """
                {"category":"FLIGHT","title":"WAW → DUB","amount":180.50,"currency":"EUR"}
                """);
        Map<String, Object> budget = addExpense(user, tripId, """
                {"category":"HOTEL","title":"Hostel","amount":220.00,"currency":"eur","spentOn":"2026-10-19"}
                """);

        assertEquals("EUR", budget.get("plannedCurrency"), "валюта має нормалізуватись у верхній регістр");
        assertEquals(0, new java.math.BigDecimal("400.50").compareTo(new java.math.BigDecimal(budget.get("spent").toString())));
        assertEquals(0, new java.math.BigDecimal("599.50").compareTo(new java.math.BigDecimal(budget.get("remaining").toString())));

        List<Map<String, Object>> expenses = (List<Map<String, Object>>) budget.get("expenses");
        assertEquals(2, expenses.size());
        // витрата без дати («на всю подорож») має йти першою
        assertNull(expenses.get(0).get("spentOn"));
        assertEquals("WAW → DUB", expenses.get(0).get("title"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void differentCurrencies_areNotSummedTogether() throws Exception {
        UUID user = UUID.randomUUID();
        String tripId = createTrip(user);

        mockMvc.perform(put("/api/trips/" + tripId + "/budget")
                        .header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00,\"currency\":\"EUR\"}"))
                .andExpect(status().isOk());

        addExpense(user, tripId, """
                {"category":"FOOD","title":"Lunch","amount":30.00,"currency":"EUR"}
                """);
        Map<String, Object> budget = addExpense(user, tripId, """
                {"category":"FOOD","title":"Снідан","amount":500.00,"currency":"UAH"}
                """);

        List<Map<String, Object>> totals = (List<Map<String, Object>>) budget.get("totals");
        assertEquals(2, totals.size(), "кожна валюта — свій підсумок");
        // у залишок потрапляє лише EUR, гривня туди не конвертується
        assertEquals(0, new java.math.BigDecimal("30.00").compareTo(new java.math.BigDecimal(budget.get("spent").toString())));
        assertEquals(0, new java.math.BigDecimal("470.00").compareTo(new java.math.BigDecimal(budget.get("remaining").toString())));
    }

    @Test
    void firstExpenseSetsPlannedCurrency_whenBudgetHasNone() throws Exception {
        UUID user = UUID.randomUUID();
        String tripId = createTrip(user);

        Map<String, Object> budget = addExpense(user, tripId, """
                {"category":"OTHER","title":"Taxi","amount":25.00,"currency":"pln"}
                """);

        assertEquals("PLN", budget.get("plannedCurrency"));
        assertNull(budget.get("plannedAmount"), "план не вигадуємо — лише валюту");
        assertNull(budget.get("remaining"), "без плану залишку бути не може");
    }

    @Test
    @SuppressWarnings("unchecked")
    void expenseCanBeDeleted_andTotalsRecalculated() throws Exception {
        UUID user = UUID.randomUUID();
        String tripId = createTrip(user);

        Map<String, Object> budget = addExpense(user, tripId, """
                {"category":"ACTIVITY","title":"Museum","amount":15.00,"currency":"EUR"}
                """);
        List<Map<String, Object>> expenses = (List<Map<String, Object>>) budget.get("expenses");
        String expenseId = (String) expenses.get(0).get("id");

        MvcResult res = mockMvc.perform(delete("/api/trips/" + tripId + "/expenses/" + expenseId)
                        .header("X-User-Id", user.toString()))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> after = asMap(res.getResponse().getContentAsString());
        assertTrue(((List<Object>) after.get("expenses")).isEmpty());
        assertTrue(((List<Object>) after.get("totals")).isEmpty());
    }

    @Test
    void strangerCannotSeeOrChangeBudget() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        String tripId = createTrip(owner);

        mockMvc.perform(get("/api/trips/" + tripId + "/budget")
                        .header("X-User-Id", stranger.toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/trips/" + tripId + "/expenses")
                        .header("X-User-Id", stranger.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"FOOD\",\"title\":\"x\",\"amount\":1.00,\"currency\":\"EUR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidCategoryOrNegativeAmount_is400() throws Exception {
        UUID user = UUID.randomUUID();
        String tripId = createTrip(user);

        mockMvc.perform(post("/api/trips/" + tripId + "/expenses")
                        .header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"CRYPTO\",\"title\":\"x\",\"amount\":1.00,\"currency\":\"EUR\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/trips/" + tripId + "/expenses")
                        .header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"FOOD\",\"title\":\"x\",\"amount\":-5.00,\"currency\":\"EUR\"}"))
                .andExpect(status().isBadRequest());
    }
}
