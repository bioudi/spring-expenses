package com.expensetracker.exception;

import com.expensetracker.dto.ErrorResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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
        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            String fieldName = ife.getPath().isEmpty() ? "value" :
                    ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String invalidValue = String.valueOf(ife.getValue());
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
