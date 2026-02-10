package com.expensetracker.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AnthropicClientService {

    @Value("${anthropic.api.key:}")
    private String apiKey;

    private AnthropicClient client;

    @PostConstruct
    void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            client = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .build();
            log.info("Anthropic client initialized (shared)");
        } else {
            log.warn("ANTHROPIC_API_KEY not set — AI features disabled");
        }
    }

    public AnthropicClient getClient() {
        return client;
    }

    public boolean isAvailable() {
        return client != null;
    }
}
