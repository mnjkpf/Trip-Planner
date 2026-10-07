package com.waylo.media.error;

import com.waylo.media.error.ApiExceptions.FileTooLargeException;
import com.waylo.media.error.ApiExceptions.InvalidTicketException;
import com.waylo.media.error.ApiExceptions.MediaNotFoundException;
import com.waylo.media.error.ApiExceptions.UnsupportedMediaTypeException;
import com.waylo.media.storage.MinioObjectStore.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidTicketException.class)
    public ProblemDetail onInvalidTicket(InvalidTicketException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({FileTooLargeException.class, MaxUploadSizeExceededException.class})
    public ProblemDetail onTooLarge(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "Файл завеликий");
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ProblemDetail onUnsupported(UnsupportedMediaTypeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ProblemDetail onNotFound(MediaNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(StorageException.class)
    public ProblemDetail onStorage(StorageException ex) {
        log.error("сховище недоступне: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Сховище недоступне");
    }
}
