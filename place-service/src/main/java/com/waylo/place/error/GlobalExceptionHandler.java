package com.waylo.place.error;

import com.waylo.place.error.ApiExceptions.PlaceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PlaceNotFoundException.class)
    public ProblemDetail handleNotFound(PlaceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // Невірний тип параметра (напр. category=xxx, якого нема в enum) → 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "Невірне значення параметра: " + e.getName());
    }

    // Немає обовʼязкового параметра (напр. lat/lon) → 400
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException e) {
        return problem(HttpStatus.BAD_REQUEST, "Відсутній параметр: " + e.getParameterName());
    }

    // Порушення @Min/@Max на параметрах (radius) → 400
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // Помилки @Valid на тілі запиту → 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        return problem(HttpStatus.BAD_REQUEST, "Помилка валідації");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
