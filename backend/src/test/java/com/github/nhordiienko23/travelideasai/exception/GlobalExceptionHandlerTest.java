package com.github.nhordiienko23.travelideasai.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Global exception handler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Returns grouped validation errors with safe fallback messages")
    void returnsGroupedValidationErrors() throws NoSuchMethodException {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "destination", "must not be blank"));
        bindingResult.addError(new FieldError("request", "destination", "must be valid"));
        bindingResult.addError(new FieldError("request", "budget", null));
        bindingResult.addError(new ObjectError("request", "request is invalid"));

        var exception = new MethodArgumentNotValidException(methodParameter(), bindingResult);

        var response = handler.handleValidationErrors(exception);

        assertProblem(response.getBody(), HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request fields contain invalid values.");
        assertThat(response.getBody().getProperties()).containsEntry("errors", Map.of(
                "destination", List.of("must not be blank", "must be valid"),
                "budget", List.of("Invalid value."),
                "_global", List.of("request is invalid")
        ));
    }

    @Test
    @DisplayName("Returns an empty error map when validation has no errors")
    void returnsEmptyValidationErrors() throws NoSuchMethodException {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        var exception = new MethodArgumentNotValidException(methodParameter(), bindingResult);

        var response = handler.handleValidationErrors(exception);

        assertProblem(response.getBody(), HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request fields contain invalid values.");
        assertThat(response.getBody().getProperties()).containsEntry("errors", Map.of());
    }

    @Test
    @DisplayName("Returns a safe error for unreadable request bodies")
    void handlesUnreadableRequest() {
        var response = handler.handleUnreadableRequest(mock(HttpMessageNotReadableException.class));

        assertProblem(response.getBody(), HttpStatus.BAD_REQUEST, "Invalid request body",
                "Request body is missing, malformed or contains invalid values.");
    }

    @Test
    @DisplayName("Returns a safe error for invalid request parameters")
    void handlesRequestConstraintErrors() {
        List<Exception> exceptions = List.of(
                mock(HandlerMethodValidationException.class),
                mock(ConstraintViolationException.class),
                mock(MethodArgumentTypeMismatchException.class),
                mock(MissingServletRequestParameterException.class)
        );

        for (Exception exception : exceptions) {
            var response = handler.handleRequestConstraintErrors(exception);

            assertProblem(response.getBody(), HttpStatus.BAD_REQUEST,
                    "Invalid request parameters",
                    "One or more request parameters contain invalid values.");
        }
    }

    @Test
    @DisplayName("Returns not found for a missing resource")
    void handlesMissingResource() {
        var response = handler.handleResourceNotFound(
                new ResourceNotFoundException("internal resource details"));

        assertProblem(response.getBody(), HttpStatus.NOT_FOUND, "Resource not found",
                "The requested resource was not found.");
    }

    @Test
    @DisplayName("Returns not found for a missing endpoint")
    void handlesMissingEndpoint() {
        var response = handler.handleEndpointNotFound(mock(NoResourceFoundException.class));

        assertProblem(response.getBody(), HttpStatus.NOT_FOUND, "Endpoint not found",
                "The requested endpoint does not exist.");
    }

    @Test
    @DisplayName("Returns a safe gateway error for invalid AI responses")
    void handlesInvalidAiResponse() {
        var response = handler.handleInvalidAiResponse(
                new InvalidAiResponseException("untrusted AI output"));

        assertProblem(response.getBody(), HttpStatus.BAD_GATEWAY, "Invalid AI response",
                "The recommendation service returned an invalid response. Please try again.");
    }

    @Test
    @DisplayName("Returns a safe unavailable response for AI service failures")
    void handlesUnavailableAiService() {
        var response = handler.handleAiServiceUnavailable(
                new AiServiceUnavailableException("internal service details"));

        assertProblem(response.getBody(), HttpStatus.SERVICE_UNAVAILABLE,
                "AI service unavailable",
                "The recommendation service is temporarily unavailable. Please try again later.");
    }

    @Test
    @DisplayName("Returns method not allowed for unsupported HTTP methods")
    void handlesUnsupportedMethod() {
        var response = handler.handleMethodNotSupported(
                mock(HttpRequestMethodNotSupportedException.class));

        assertProblem(response.getBody(), HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
                "The requested HTTP method is not supported for this endpoint.");
    }

    @Test
    @DisplayName("Returns unsupported media type for an unsupported request content type")
    void handlesUnsupportedMediaType() {
        var response = handler.handleUnsupportedMediaType(
                mock(HttpMediaTypeNotSupportedException.class));

        assertProblem(response.getBody(), HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type", "The request content type is not supported.");
    }

    @Test
    @DisplayName("Returns not acceptable for an unsupported response format")
    void handlesNotAcceptable() {
        var response = handler.handleNotAcceptable(
                mock(HttpMediaTypeNotAcceptableException.class));

        assertProblem(response.getBody(), HttpStatus.NOT_ACCEPTABLE, "Not acceptable",
                "The requested response format is not supported.");
    }

    @Test
    @DisplayName("Returns a safe internal error without exposing exception details")
    void handlesUnexpectedError() {
        var response = handler.handleUnexpectedError(
                new IllegalStateException("sensitive internal details"));

        assertProblem(response.getBody(), HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred. Please try again later.");
    }

    private MethodParameter methodParameter() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("parameterTarget", String.class);
        return new MethodParameter(method, 0);
    }

    private void parameterTarget(String value) {
    }

    private void assertProblem(
            ProblemDetail problem,
            HttpStatus status,
            String title,
            String detail
    ) {
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(status.value());
        assertThat(problem.getTitle()).isEqualTo(title);
        assertThat(problem.getDetail()).isEqualTo(detail);
    }
}