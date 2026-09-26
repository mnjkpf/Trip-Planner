package com.travelplanner.planner.planning;

import com.travelplanner.planner.client.PlaceClient;
import com.travelplanner.planner.client.PlaceDto;
import com.travelplanner.planner.itinerary.ItineraryDay;
import com.travelplanner.planner.itinerary.ItineraryPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Ядро планувальника: дістати POI навколо призначення з place-service,
 * побудувати маршрут по днях і опублікувати результат подією trip.plan.completed.
 */
@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);
    private static final int SEARCH_RADIUS_METERS = 5000;

    private final PlaceClient placeClient;
    private final ItineraryPlanner itineraryPlanner;
    private final PlanResultPublisher publisher;

    public PlanningService(PlaceClient placeClient,
                           ItineraryPlanner itineraryPlanner,
                           PlanResultPublisher publisher) {
        this.placeClient = placeClient;
        this.itineraryPlanner = itineraryPlanner;
        this.publisher = publisher;
    }

    public void plan(PlanRequest req) {
        List<PlaceDto> places = placeClient.searchNearby(
                req.destinationLat(), req.destinationLon(), SEARCH_RADIUS_METERS);

        List<ItineraryDay> days = itineraryPlanner.build(places, req.startDate(), req.endDate());

        publisher.publishCompleted(new PlanCompletedEvent(
                req.jobId(), req.tripId(), req.userId(), "COMPLETED", days));

        log.info("маршрут для job {} побудовано: {} днів, {} точок",
                req.jobId(), days.size(), places.size());
    }
}
