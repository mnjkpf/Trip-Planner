package com.waylo.user.error;

public final class ApiExceptions {

    private ApiExceptions() {}

    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException(String msg) { super(msg); }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String msg) { super(msg); }
    }

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String msg) { super(msg); }
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String msg) { super(msg); }
    }
}
