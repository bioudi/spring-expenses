package com.expensetracker.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.expensetracker.config.ExpenseCategory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CategorizationService {

    @Value("${anthropic.api.key:}")
    private String apiKey;

    private AnthropicClient client;

    private final Map<String, String> categoryCache = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT =
            "You are an expense categorizer for someone living in Quebec City, Canada. " +
            "Given a merchant name, return exactly one of these categories: " +
            String.join(", ", ExpenseCategory.VALID_CATEGORIES) + ". " +
            "Use your knowledge of Quebec City businesses and common merchant names. Examples: " +
            "Maxi, IGA, Metro, Super C, Provigo → Groceries. " +
            "McDonald's, Subway, Tim Hortons, A&W → Fast Food. " +
            "Starbucks, Second Cup, Van Houtte → Coffee & Cafes. " +
            "Canadian Tire, Walmart, Amazon → Electronics or Home & Garden depending on context. " +
            "STM, RTC → Public Transit. Uber, Lyft → Rideshare & Taxi. " +
            "Shell, Petro-Canada, Esso, Couche-Tard (gas) → Gas & Fuel. " +
            "Jean Coutu, Pharmaprix → Pharmacy. " +
            "Netflix, Spotify, Disney+ → Streaming. " +
            "Hydro-Quebec, Bell, Videotron → Utilities or Internet & Phone. " +
            "Reply with ONLY the category name, nothing else.";

    @PostConstruct
    void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            client = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .build();
            log.info("Anthropic client initialized for expense categorization");
        } else {
            log.warn("ANTHROPIC_API_KEY not set — AI categorization disabled, expenses without a category will be 'Uncategorized'");
        }
    }

    public String categorize(String merchant) {
        if (client == null) {
            log.warn("Skipping AI categorization — Anthropic client not initialized (is ANTHROPIC_API_KEY set?)");
            return null;
        }
        if (merchant == null || merchant.isBlank()) {
            log.warn("Skipping AI categorization — merchant name is null or blank");
            return null;
        }

        String normalizedMerchant = merchant.trim().toLowerCase();

        String cached = categoryCache.get(normalizedMerchant);
        if (cached != null) {
            log.info("Cache hit — merchant '{}' already categorized as '{}'", merchant, cached);
            return cached;
        }

        log.info("Cache miss — calling Claude API to categorize merchant: '{}'", merchant);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_HAIKU_4_5_20251001)
                    .maxTokens(50L)
                    .system(SYSTEM_PROMPT)
                    .addUserMessage("Merchant: " + merchant)
                    .build();

            Message message = client.messages().create(params);
            String category = message.content().get(0).asText().text().trim();

            if (ExpenseCategory.isValid(category)) {
                categoryCache.put(normalizedMerchant, category);
                log.info("AI categorized merchant '{}' as '{}' (cached for future use, cache size: {})", merchant, category, categoryCache.size());
                return category;
            } else {
                log.warn("AI returned invalid category '{}' for merchant '{}', falling back to Uncategorized", category, merchant);
                return null;
            }
        } catch (Exception e) {
            log.error("AI categorization failed for merchant '{}': {} - {}", merchant, e.getClass().getSimpleName(), e.getMessage(), e);
            return null;
        }
    }
}
