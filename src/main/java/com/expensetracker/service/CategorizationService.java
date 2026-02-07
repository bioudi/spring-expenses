package com.expensetracker.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.expensetracker.config.ExpenseCategory;
import com.expensetracker.entity.MerchantCategory;
import com.expensetracker.repository.MerchantCategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CategorizationService {

    @Value("${anthropic.api.key:}")
    private String apiKey;

    private AnthropicClient client;

    private final Map<String, String> categoryCache = new ConcurrentHashMap<>();

    private final MerchantCategoryRepository merchantCategoryRepository;

    public CategorizationService(MerchantCategoryRepository merchantCategoryRepository) {
        this.merchantCategoryRepository = merchantCategoryRepository;
    }

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

        // Load persistent cache from database
        List<MerchantCategory> mappings = merchantCategoryRepository.findAll();
        for (MerchantCategory mc : mappings) {
            categoryCache.put(mc.getMerchantKey(), mc.getCategory());
        }
        log.info("Loaded {} merchant→category mappings from database into cache", mappings.size());
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

                // Persist to database
                MerchantCategory mc = MerchantCategory.builder()
                        .merchantKey(normalizedMerchant)
                        .category(category)
                        .build();
                merchantCategoryRepository.save(mc);

                log.info("AI categorized merchant '{}' as '{}' (saved to DB, cache size: {})", merchant, category, categoryCache.size());
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

    // --- Methods for Merchant UI ---

    public List<MerchantCategory> getAllMappings() {
        return merchantCategoryRepository.findAll();
    }

    public MerchantCategory updateMapping(UUID id, String category) {
        MerchantCategory mc = merchantCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Merchant mapping not found: " + id));
        mc.setCategory(category);
        merchantCategoryRepository.save(mc);
        categoryCache.put(mc.getMerchantKey(), category);
        log.info("Updated merchant mapping: '{}' → '{}'", mc.getMerchantKey(), category);
        return mc;
    }

    public void deleteMapping(UUID id) {
        MerchantCategory mc = merchantCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Merchant mapping not found: " + id));
        merchantCategoryRepository.delete(mc);
        categoryCache.remove(mc.getMerchantKey());
        log.info("Deleted merchant mapping: '{}' (was '{}')", mc.getMerchantKey(), mc.getCategory());
    }
}
