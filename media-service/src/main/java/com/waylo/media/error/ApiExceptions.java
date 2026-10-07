package com.waylo.media.error;

/** Помилки медіа-сервісу з однозначним HTTP-кодом (див. GlobalExceptionHandler). */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** Тікет невідомий, протермінований або вже погашений. */
    public static class InvalidTicketException extends RuntimeException {
        public InvalidTicketException(String message) {
            super(message);
        }
    }

    public static class FileTooLargeException extends RuntimeException {
        public FileTooLargeException(String message) {
            super(message);
        }
    }

    public static class UnsupportedMediaTypeException extends RuntimeException {
        public UnsupportedMediaTypeException(String message) {
            super(message);
        }
    }

    public static class MediaNotFoundException extends RuntimeException {
        public MediaNotFoundException(String message) {
            super(message);
        }
    }
}
