package com.travelplanner.trip.error;

public final class ApiExceptions {

    private ApiExceptions() {}

    public static class TripNotFoundException extends RuntimeException {
        public TripNotFoundException(String msg) { super(msg); }
    }
}
