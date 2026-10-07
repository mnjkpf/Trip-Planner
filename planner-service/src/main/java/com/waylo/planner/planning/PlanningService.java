package com.waylo.planner.planning;

import com.waylo.planner.client.ContextClient;
import com.waylo.planner.client.DestinationContext;
import com.waylo.planner.client.PlaceClient;
import com.waylo.planner.client.PlaceDto;
import com.waylo.planner.itinerary.ItineraryDay;
import com.waylo.planner.itinerary.ItineraryPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ядро планувальника: дістати POI (place-service), збагатити контекстом
 * (context-service) і опублікувати маршрут подією trip.plan.completed.
 *
 * Побажання користувача (темп, інтереси, радіус, початок дня) — опційні:
 * кожне має свій дефолт, тож подорож без жодного вибраного фільтра планується
 * так само, як і до появи фільтрів.
 */
@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

    private static final int DEFAULT_RADIUS_METERS = 5000;
    private static final int MIN_RADIUS_METERS = 1000;
    private static final int MAX_RADIUS_METERS = 50_000;

    private final PlaceClient placeClient;
    private final ContextClient contextClient;
    private final ItineraryPlanner itineraryPlanner;
    private final PlanResultPublisher publisher;

    public PlanningService(PlaceClient placeClient,
                           ContextClient contextClient,
                           ItineraryPlanner itineraryPlanner,
                           PlanResultPublisher publisher) {
        this.placeClient = placeClient;
        this.contextClient = contextClient;
        this.itineraryPlanner = itineraryPlanner;
        this.publisher = publisher;
    }

    public void plan(PlanRequest req) {
        int radius = radiusOf(req.searchRadiusM());

        List<PlaceDto> found = placeClient.searchNearby(
                req.destinationLat(), req.destinationLon(), radius);

        List<PlaceDto> places = prioritizeByInterests(found, req.interests());

        List<ItineraryDay> days = itineraryPlanner.build(
                places, req.startDate(), req.endDate(), req.pace(), req.dayStartTime());

        DestinationContext context = contextClient.fetch(
                req.destinationLat(), req.destinationLon(), req.startDate(), req.endDate());

        publisher.publishCompleted(new PlanCompletedEvent(
                req.jobId(), req.tripId(), req.userId(), "COMPLETED",
                context.season(), context.climateHint(), days));

        int scheduled = days.stream().mapToInt(d -> d.items().size()).sum();
        log.info("маршрут для job {} побудовано: {} днів, {} з {} точок, радіус={}м, "
                        + "темп={}, інтереси={}, старт дня={}, сезон={}",
                req.jobId(), days.size(), scheduled, places.size(), radius,
                req.pace() == null ? "—" : req.pace(),
                req.interests().isEmpty() ? "—" : req.interests(),
                req.dayStartTime() == null ? "—" : req.dayStartTime(),
                context.season());
    }

    /** Радіус пошуку POI: вибір користувача в розумних межах, інакше дефолт. */
    private static int radiusOf(Integer requested) {
        if (requested == null) {
            return DEFAULT_RADIUS_METERS;
        }
        return Math.min(MAX_RADIUS_METERS, Math.max(MIN_RADIUS_METERS, requested));
    }

    /**
     * Стабільно піднімає наперед POI, чия категорія є серед інтересів. Порядок
     * усередині обох груп зберігається (place-service уже віддає їх за відстанню),
     * тому результат = «цікаве ближче спочатку, решта — далі». Нічого не викидаємо:
     * відсіканням зайвого займається ліміт темпу в ItineraryPlanner, і якщо
     * інтересів у місті мало, маршрут усе одно наповниться іншими місцями.
     */
    static List<PlaceDto> prioritizeByInterests(List<PlaceDto> places, List<String> interests) {
        if (places.isEmpty() || interests == null || interests.isEmpty()) {
            return places;
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String i : interests) {
            if (i != null && !i.isBlank()) {
                wanted.add(i.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (wanted.isEmpty()) {
            return places;
        }

        List<PlaceDto> matched = new ArrayList<>();
        List<PlaceDto> rest = new ArrayList<>();
        for (PlaceDto p : places) {
            String category = p.category() == null ? "" : p.category().toUpperCase(Locale.ROOT);
            if (wanted.contains(category)) {
                matched.add(p);
            } else {
                rest.add(p);
            }
        }
        if (matched.isEmpty()) {
            log.info("жодне POI не підпало під інтереси {} — залишаю порядок за відстанню", wanted);
            return places;
        }

        List<PlaceDto> ordered = new ArrayList<>(places.size());
        ordered.addAll(matched);
        ordered.addAll(rest);
        return ordered;
    }
}
