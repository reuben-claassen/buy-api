package com.buyapi.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * Handlers are invoked directly (no Spring context).
 * Tests verify HTTP status, error type, message, and metadata.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFound_returns404WithCorrectType() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Product not found with id: 42");

        ProblemDetail pd = handler.handleNotFound(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getDetail()).isEqualTo("Product not found with id: 42");
        assertThat(pd.getType()).isEqualTo(URI.create("/errors/not-found"));
        assertThat(pd.getProperties()).containsKey("timestamp");
    }

    @Test
    void handleNotFound_resourceAndIdConstructor_formatsMessageCorrectly() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Order", 99L);

        ProblemDetail pd = handler.handleNotFound(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getDetail()).isEqualTo("Order not found with id: 99");
    }

    @Test
    void handleBadRequest_returns400WithCorrectType() {
        BadRequestException ex = new BadRequestException("Email already in use");

        ProblemDetail pd = handler.handleBadRequest(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getDetail()).isEqualTo("Email already in use");
        assertThat(pd.getType()).isEqualTo(URI.create("/errors/bad-request"));
        assertThat(pd.getProperties()).containsKey("timestamp");
    }

    @Test
    void handleValidation_returns400WithErrorMap() throws Exception {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must be a valid email"));
        bindingResult.addError(new FieldError("request", "password", "must not be blank"));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ProblemDetail pd = handler.handleValidation(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getDetail()).isEqualTo("Validation failed");
        assertThat(pd.getType()).isEqualTo(URI.create("/errors/validation"));
        assertThat(pd.getProperties()).containsKey("timestamp");

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) pd.getProperties().get("errors");
        assertThat(errors)
                .containsEntry("email", "must be a valid email")
                .containsEntry("password", "must not be blank");
    }

    @Test
    void handleValidation_duplicateField_keepsFirstMessage() throws Exception {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "first error"));
        bindingResult.addError(new FieldError("request", "email", "second error"));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ProblemDetail pd = handler.handleValidation(ex);

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) pd.getProperties().get("errors");
        assertThat(errors.get("email")).isEqualTo("first error");
    }

    @Test
    void handleValidation_nullDefaultMessage_fallsBackToInvalidValue() throws Exception {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", null, false, null, null, null));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ProblemDetail pd = handler.handleValidation(ex);

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) pd.getProperties().get("errors");
        assertThat(errors.get("name")).isEqualTo("Invalid value");
    }

    @Test
    void handleAccessDenied_returns403WithCorrectType() {
        AccessDeniedException ex = new AccessDeniedException("Access is denied");

        ProblemDetail pd = handler.handleAccessDenied(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(pd.getDetail()).isEqualTo("Access denied");
        assertThat(pd.getType()).isEqualTo(URI.create("/errors/forbidden"));
        assertThat(pd.getProperties()).containsKey("timestamp");
    }

    @Test
    void handleAuth_returns401WithCorrectType() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ProblemDetail pd = handler.handleAuth(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(pd.getDetail()).isEqualTo("Bad credentials");
        assertThat(pd.getType()).isEqualTo(URI.create("/errors/unauthorized"));
        assertThat(pd.getProperties()).containsKey("timestamp");
    }

    @Test
    void handleGeneric_returns500WithSafeMessage() {
        RuntimeException ex = new RuntimeException("Something blew up internally");

        ProblemDetail pd = handler.handleGeneric(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(pd.getType()).isEqualTo(URI.create("/errors/internal"));
        assertThat(pd.getProperties()).containsKey("timestamp");
    }

    @Test
    void handleGeneric_detailIsAlwaysGeneric_regardlessOfExceptionMessage() {
        RuntimeException ex = new RuntimeException("DB password is hunter2");

        ProblemDetail pd = handler.handleGeneric(ex);

        assertThat(pd.getDetail()).doesNotContain("hunter2");
        assertThat(pd.getDetail()).isEqualTo("An unexpected error occurred");
    }
}