package com.github.nhordiienko23.travelideasai.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationErrors(
            MethodArgumentNotValidException exception
    ) {
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request fields contain invalid values."
        );

        Map<String, List<String>> errors = new LinkedHashMap<>();

        for (ObjectError error :
                exception.getBindingResult().getAllErrors()) {

            String field = error instanceof FieldError fieldError
                    ? fieldError.getField()
                    : "_global";

            String message = error.getDefaultMessage() != null
                    ? error.getDefaultMessage()
                    : "Invalid value.";

            errors.computeIfAbsent(field, key -> new ArrayList<>())
                    .add(message);
        }

        problem.setProperty("errors", errors);

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableRequest(
            HttpMessageNotReadableException exception
    ) {
        return createResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request body",
                "Request body is missing, malformed or contains invalid values."
        );
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ProblemDetail> handleRequestConstraintErrors(
            Exception exception
    ) {
        return createResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request parameters",
                "One or more request parameters contain invalid values."
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(
            ResourceNotFoundException exception
    ) {
        return createResponse(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                "The requested resource was not found."
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleEndpointNotFound(
            NoResourceFoundException exception
    ) {
        return createResponse(
                HttpStatus.NOT_FOUND,
                "Endpoint not found",
                "The requested endpoint does not exist."
        );
    }

    @ExceptionHandler(InvalidAiResponseException.class)
    public ResponseEntity<ProblemDetail> handleInvalidAiResponse(
            InvalidAiResponseException exception
    ) {
        log.warn("AI returned a response that failed validation.", exception);

        return createResponse(
                HttpStatus.BAD_GATEWAY,
                "Invalid AI response",
                "The recommendation service returned an invalid response. Please try again."
        );
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleAiServiceUnavailable(
            AiServiceUnavailableException exception
    ) {
        log.warn("AI service is unavailable.", exception);

        return createResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI service unavailable",
                "The recommendation service is temporarily unavailable. Please try again later."
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception
    ) {
        return createResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed",
                "The requested HTTP method is not supported for this endpoint."
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception
    ) {
        return createResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type",
                "The request content type is not supported."
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ProblemDetail> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException exception
    ) {
        return createResponse(
                HttpStatus.NOT_ACCEPTABLE,
                "Not acceptable",
                "The requested response format is not supported."
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedError(
            Exception exception
    ) {
        log.error("Unexpected error while processing the request.", exception);

        return createResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred. Please try again later."
        );
    }

    private ResponseEntity<ProblemDetail> createResponse(
            HttpStatus status,
            String title,
            String detail
    ) {
        ProblemDetail problem = createProblem(status, title, detail);

        return ResponseEntity.status(status).body(problem);
    }

    private ProblemDetail createProblem(
            HttpStatus status,
            String title,
            String detail
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(status, detail);

        problem.setTitle(title);

        return problem;
    }
}