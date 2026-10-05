package com.waylo.trip.error;

import com.waylo.trip.error.ApiExceptions.TripConflictException;
import com.waylo.trip.error.ApiExceptions.TripForbiddenException;
import com.waylo.trip.error.ApiExceptions.TripNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TripNotFoundException.class)
    public ProblemDetail handleNotFound(TripNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // Учасник є, але роль нижча за потрібну.
    @ExceptionHandler(TripForbiddenException.class)
    public ProblemDetail handleForbidden(TripForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage());
    }

    // Конфлікт стану: наприклад, редагування під час планування.
    @ExceptionHandler(TripConflictException.class)
    public ProblemDetail handleConflict(TripConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    // Крос-польова валідація в сервісі (напр. endDate < startDate).
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadArg(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // Немає X-User-Id → запит прийшов не через gateway або без токена
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Потрібна автентифікація");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Помилка валідації");
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        pd.setProperty("errors", errors);
        return pd;
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
