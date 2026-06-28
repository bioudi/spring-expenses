package com.expensetracker.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.UUID;

/**
 * Strict {@link UUID} deserializer that rejects empty / blank strings
 * instead of silently coercing them to {@code null}.
 *
 * <p>Spring Boot's default {@code ObjectMapper} enables Jackson's
 * {@code ACCEPT_EMPTY_STRING_AS_NULL_OBJECT} feature, which means an
 * empty string bound to a {@code UUID} field becomes {@code null} with
 * no error. For optional identifiers like {@code accountId} that is
 * misleading: a caller sending {@code "accountId": ""} is clearly not
 * saying "omit it" — they are sending a malformed value. We want to
 * reject that with HTTP 400, not silently produce an unlinked expense.
 *
 * <p>This deserializer:
 * <ul>
 *   <li>Treats a JSON {@code null} literal as {@code null}, preserving the
 *       optional-field contract.</li>
 *   <li>Treats an empty or whitespace-only string as a parse failure,
 *       raising an {@link InvalidFormatException} so the
 *       {@code GlobalExceptionHandler} surfaces a clear 400.</li>
 *   <li>Treats any other non-UUID string as a parse failure (Jackson's
 *       default behaviour, surfaced as 400 by the handler).</li>
 * </ul>
 */
@Slf4j
public class StrictUuidDeserializer extends JsonDeserializer<UUID> {

    @Override
    public UUID deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        // Explicit JSON null → leave it null (the field is optional).
        if (p.currentToken() == com.fasterxml.jackson.core.JsonToken.VALUE_NULL) {
            return null;
        }

        // For a missing field, Jackson does not call the deserializer at all
        // (it leaves the property unset), so we only see a scalar token here
        // when the JSON actually contains a value for the field.
        String raw = p.getValueAsString();
        String fieldName = p.currentName() != null ? p.currentName() : "accountId";

        if (raw == null || raw.isBlank()) {
            // Empty / whitespace string is the bug we are fixing: it used to
            // slip through as null because of Jackson's
            // ACCEPT_EMPTY_STRING_AS_NULL_OBJECT default. Surface it as an
            // invalid UUID so GlobalExceptionHandler returns HTTP 400.
            log.warn("Rejecting request — field '{}' received empty/blank UUID string '{}'",
                    fieldName, raw);
            throw InvalidFormatException.from(p, fieldName, raw, UUID.class);
        }

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            // Delegate the standard "not a UUID" path so the error shape matches.
            throw InvalidFormatException.from(p, fieldName, raw, UUID.class);
        }
    }
}