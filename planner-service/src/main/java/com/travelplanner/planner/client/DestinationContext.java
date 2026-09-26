package com.travelplanner.planner.client;

/** Стислий контекст напряму від context-service (сезон + підказка). Поля можуть бути null. */
public record DestinationContext(String season, String climateHint) {

    public static DestinationContext empty() {
        return new DestinationContext(null, null);
    }
}
