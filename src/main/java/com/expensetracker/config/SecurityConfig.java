package com.expensetracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${dashboard.username:admin}")
    private String username;

    @Value("${dashboard.password:admin}")
    private String password;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Webhook uses its own API key auth — let it through Spring Security
                        .requestMatchers("/api/webhook/**").permitAll()
                        // Login page is public
                        .requestMatchers("/login.html", "/login").permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login.html?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login.html?logout=true")
                        .permitAll()
                )
                .httpBasic(basic -> {})
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/login", "/logout")
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        var user = User.builder()
                .username(username)
                .password("{noop}" + password)
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(user);
    }
}
