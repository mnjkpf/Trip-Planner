package com.waylo.trip.error;

public final class ApiExceptions {

    private ApiExceptions() {}

    public static class TripNotFoundException extends RuntimeException {
        public TripNotFoundException(String msg) { super(msg); }
    }

    // Редагування заблоковане, бо подорож саме планується (async-петля в польоті).
    public static class TripConflictException extends RuntimeException {
        public TripConflictException(String msg) { super(msg); }
    }

    /**
     * Доступ до подорожі є, але ролі не вистачає — глядач намагається редагувати.
     * Саме 403, а не 404: приховувати існування подорожі від її ж учасника безглуздо.
     */
    public static class TripForbiddenException extends RuntimeException {
        public TripForbiddenException(String msg) { super(msg); }
    }
}
