package com.expensetracker.exception;

import com.expensetracker.dto.ErrorResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> ErrorResponse.FieldError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid request data")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(InvalidCategoryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCategoryException(
            InvalidCategoryException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid Category")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ExpenseNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleExpenseNotFoundException(
            ExpenseNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(RecurringExpenseNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRecurringExpenseNotFoundException(
            RecurringExpenseNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFoundException(
            AccountNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Maps {@link AccountHasLinkedExpensesException} to a 400 with a clear
     * message naming the account id and the number of linked expenses. This
     * is the contract advertised by {@code DELETE /api/accounts/:id} — the
     * 500 that preceded this handler masked the real cause (foreign-key
     * violation) behind a generic "An unexpected error occurred".
     */
    @ExceptionHandler(AccountHasLinkedExpensesException.class)
    public ResponseEntity<ErrorResponse> handleAccountHasLinkedExpensesException(
            AccountHasLinkedExpensesException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Account Has Linked Expenses")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Safety net for foreign-key violations that escape the service layer
     * (e.g. a race condition where a concurrent request links a row between
     * the service's pre-check and its delete). Without this handler these
     * surface as raw 500s with a generic message; with it, we return a 400
     * whose message names the violated constraint so the caller knows which
     * linked resource to clean up.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        String message = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        log.warn("Data-integrity violation at {}: {}", request.getRequestURI(), message);

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Data Integrity Violation")
                .message("Operation violates a database constraint: " + message)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(BudgetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBudgetNotFoundException(
            BudgetNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IncomeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleIncomeNotFoundException(
            IncomeNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(
            InsufficientFundsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Insufficient Funds")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.unprocessableEntity().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        // If the body is malformed because a string couldn't be coerced to an enum
        // (e.g. account type "Savings" instead of "SAVINGS"), surface a clearer
        // message than the generic "Malformed JSON".
        Throwable cause = ex.getCause();

        // Unknown property (application.properties enables
        // spring.jackson.deserialization.fail-on-unknown-properties=true, so
        // any unrecognised key in a request body lands here). Surface the
        // offending field name so the caller knows
        // which one to drop instead of seeing a generic "Malformed JSON".
        if (cause instanceof UnrecognizedPropertyException upe) {
            String fieldName = upe.getPropertyName();
            List<String> knownFields = upe.getKnownPropertyIds().stream()
                    .map(Object::toString)
                    .sorted()
                    .collect(Collectors.toList());
            log.debug("Rejecting request to {} — unknown field '{}'", request.getRequestURI(), fieldName);
            ErrorResponse response = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpStatus.BAD_REQUEST.value())
                    .error("Unknown field '" + fieldName + "'")
                    .message("Field '" + fieldName + "' is not recognized. Allowed fields: "
                            + String.join(", ", knownFields))
                    .path(request.getRequestURI())
                    .build();
            return ResponseEntity.badRequest().body(response);
        }

        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null) {
            String fieldName = ife.getPath().isEmpty() ? "value" :
                    ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String invalidValue = String.valueOf(ife.getValue());

            // Enum → list valid values, same shape as before.
            if (ife.getTargetType().isEnum()) {
                String validValues = Arrays.stream(ife.getTargetType().getEnumConstants())
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                ErrorResponse response = ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Invalid " + ife.getTargetType().getSimpleName())
                        .message("Invalid value '" + invalidValue + "' for " + fieldName +
                                ". Valid values: " + validValues)
                        .path(request.getRequestURI())
                        .build();
                return ResponseEntity.badRequest().body(response);
            }

            // UUID → clear "invalid format" message instead of generic "Malformed JSON".
            if (UUID.class.isAssignableFrom(ife.getTargetType())) {
                log.debug("Rejecting request to {} — {} '{}' is not a valid UUID",
                        request.getRequestURI(), fieldName, invalidValue);
                ErrorResponse response = ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Invalid " + fieldName + " format")
                        .message("Invalid account_id format: '" + invalidValue +
                                "' is not a valid UUID. Provide a value like 550e8400-e29b-41d4-a716-446655440000.")
                        .path(request.getRequestURI())
                        .build();
                return ResponseEntity.badRequest().body(response);
            }
        }

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Malformed JSON")
                .message("Request body is not valid JSON")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles path-variable type-conversion failures (e.g. a non-UUID passed
     * where a {@link UUID} is expected). Treats a non-UUID account id the
     * same as a valid-but-unknown one — both surface as 404, since the
     * resource the caller is asking for cannot exist.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        Class<?> requiredType = ex.getRequiredType();
        if (requiredType != null && UUID.class.isAssignableFrom(requiredType)) {
            // Treat any non-UUID account id as "not found" so the client sees a
            // consistent 404 regardless of whether the id is malformed or
            // simply not in the database.
            log.debug("Rejecting request to {} — {} '{}' is not a valid UUID",
                    request.getRequestURI(), ex.getName(), ex.getValue());
            ErrorResponse response = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpStatus.NOT_FOUND.value())
                    .error("Not Found")
                    .message("Account not found with id: " + ex.getValue())
                    .path(request.getRequestURI())
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        // Generic fallback for non-UUID type mismatches: 400 Bad Request.
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Parameter '" + ex.getName() + "' has invalid value: " + ex.getValue())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Honours {@link ResponseStatusException}s thrown from services. Without
     * this handler, the generic {@code @ExceptionHandler(Exception.class)}
     * below would convert any 4xx {@code ResponseStatusException} into a 500
     * — e.g. {@code IncomeService.validateAccountIfProvided} throws
     * {@code new ResponseStatusException(NOT_FOUND, "...")} and the caller
     * would see a misleading "Internal Server Error" instead of 404.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getReason() != null ? ex.getReason() : status.getReasonPhrase())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Internal Server Error at {}: {} - {}",
                request.getRequestURI(),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex);

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
