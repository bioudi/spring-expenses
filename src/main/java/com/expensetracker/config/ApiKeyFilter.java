package com.expensetracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${api.key}")
    private String apiKey;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Only apply API key check to webhook endpoints
        if (requestPath.startsWith("/api/webhook")) {
            String providedApiKey = request.getHeader(API_KEY_HEADER);

            if (providedApiKey == null || providedApiKey.isBlank()) {
                sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "Missing API key");
                return;
            }

            if (!apiKey.equals(providedApiKey)) {
                sendErrorResponse(response, HttpStatus.FORBIDDEN, "Invalid API key");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                String.format("{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                        java.time.LocalDateTime.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message)
        );
    }
}
