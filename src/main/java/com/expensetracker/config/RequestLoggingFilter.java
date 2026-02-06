package com.expensetracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/api/webhook")) {
            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

            try {
                filterChain.doFilter(wrappedRequest, response);
            } finally {
                String body = new String(wrappedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);
                log.info("Webhook request: method={}, uri={}, body={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        body);
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
