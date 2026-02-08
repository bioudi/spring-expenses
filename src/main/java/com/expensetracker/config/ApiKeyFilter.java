package com.expensetracker.config;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final UserRepository userRepository;

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

            // Look up user by API key
            Optional<User> userOpt = userRepository.findByApiKey(providedApiKey);
            if (userOpt.isEmpty()) {
                sendErrorResponse(response, HttpStatus.FORBIDDEN, "Invalid API key");
                return;
            }

            // Set SecurityContext so controllers can use SecurityUtils.getCurrentUserId()
            User user = userOpt.get();
            UserPrincipal principal = new UserPrincipal(
                    user.getId(),
                    user.getEmail(),
                    user.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
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
