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

        if (!request.getRequestURI().startsWith("/api/webhook")) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        StringBuilder headers = new StringBuilder();
        java.util.Collections.list(request.getHeaderNames()).forEach(name ->
                headers.append(name).append("=").append(request.getHeader(name)).append(" "));

        Throwable error = null;
        try {
            filterChain.doFilter(wrappedRequest, response);
        } catch (Exception e) {
            error = e;
            throw e;
        } finally {
            String body = new String(wrappedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);
            if (error != null) {
                log.error("Webhook FAILED: method={} uri={} status={} headers=[{}] body=[{}] error={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(),
                        headers, body.isBlank() ? "<empty>" : body, error.getMessage());
            } else {
                log.info("Webhook OK: method={} uri={} status={} headers=[{}] body=[{}]",
                        request.getMethod(), request.getRequestURI(), response.getStatus(),
                        headers, body.isBlank() ? "<empty>" : body);
            }
        }
    }
}
