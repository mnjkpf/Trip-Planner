package com.travelplanner.place.error;

public final class ApiExceptions {

    private ApiExceptions() {}

    public static class PlaceNotFoundException extends RuntimeException {
        public PlaceNotFoundException(String msg) { super(msg); }
    }
}
