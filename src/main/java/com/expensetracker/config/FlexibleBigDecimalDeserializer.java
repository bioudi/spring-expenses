package com.expensetracker.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;

@Slf4j
public class FlexibleBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String rawValue = p.getText();
        log.info("Deserializing amount - raw value: '{}', token type: {}", rawValue, p.currentToken());

        if (rawValue == null || rawValue.trim().isEmpty()) {
            log.warn("Amount is null or empty");
            return null;
        }

        try {
            // Remove any currency symbols, commas, spaces
            String cleanedValue = rawValue
                    .replaceAll("[^\\d.,-]", "")  // Keep only digits, dots, commas, minus
                    .replace(",", ".");            // Replace comma with dot for decimals

            log.info("Cleaned amount value: '{}'", cleanedValue);

            if (cleanedValue.isEmpty()) {
                return null;
            }

            return new BigDecimal(cleanedValue);
        } catch (NumberFormatException e) {
            log.error("Failed to parse amount '{}': {}", rawValue, e.getMessage());
            return null;
        }
    }
}
