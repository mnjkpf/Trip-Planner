package com.travelplanner.trip.error;

public final class ApiExceptions {

    private ApiExceptions() {}

    public static class TripNotFoundException extends RuntimeException {
        public TripNotFoundException(String msg) { super(msg); }
    }

    // Редагування заблоковане, бо подорож саме планується (async-петля в польоті).
    public static class TripConflictException extends RuntimeException {
        public TripConflictException(String msg) { super(msg); }
    }
}
