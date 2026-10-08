package com.waylo.planner.web;

import com.waylo.planner.planning.PlanOutcome;
import com.waylo.planner.planning.PlanRequest;
import com.waylo.planner.planning.PlanningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.temporal.ChronoUnit;

/**
 * Прев'ю маршруту для людини без акаунта.
 *
 * Навмисно синхронний, на відміну від основного планування через Kafka:
 * асинхронна гілка існує, щоб збережена подорож пережила падіння сервісу
 * й щоб клієнт не чекав на відповідь — для цього є job у базі, outbox і SSE.
 * Прев'ю нічого не зберігає взагалі, тож усієї тієї машинерії тут нема чому
 * служити: немає подорожі, до якої приклеїти результат, і немає кому його
 * дослати. Відповідь живе лише у вкладці гостя.
 *
 * Шлях під /api/public/**, тож gateway пускає без токена.
 */
@RestController
@RequestMapping("/api/public/plan")
public class PreviewController {

    /** Стеля на прев'ю: довші подорожі плануються вже в акаунті. */
    private static final int MAX_PREVIEW_DAYS = 14;

    private final PlanningService planningService;

    public PreviewController(PlanningService planningService) {
        this.planningService = planningService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public PlanOutcome preview(@Valid @RequestBody PreviewRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate раніше за startDate");
        }
        long days = ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1;
        if (days > MAX_PREVIEW_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "прев'ю обмежене " + MAX_PREVIEW_DAYS + " днями");
        }

        return planningService.build(new PlanRequest(
                null, null, null,
                req.destinationLat(), req.destinationLon(),
                req.startDate(), req.endDate(),
                req.pace(), req.interests(), req.searchRadiusM(), req.dayStartTime()));
    }
}
