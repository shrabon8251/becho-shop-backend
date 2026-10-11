package com.becho.saas.security;

import com.becho.saas.tenancy.TenantFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final TenantFilter tenantFilter;
    private final RateLimitFilter rateLimitFilter;
    private final ApiKeyAuthFilter apiKeyAuthFilter;

    @Value("${becho.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, TenantFilter tenantFilter,
                          RateLimitFilter rateLimitFilter, ApiKeyAuthFilter apiKeyAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.tenantFilter = tenantFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.apiKeyAuthFilter = apiKeyAuthFilter;
    }

    /** Argon2id — memory-hard, the current best practice for password hashing. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 1 << 14, 3);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless JWT API
                .cors(c -> c.configurationSource(req -> {
                    var cfg = new CorsConfiguration();
                    cfg.setAllowedOrigins(List.of(allowedOrigins.split(",")));
                    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                    cfg.setAllowedHeaders(List.of("*"));
                    cfg.setAllowCredentials(true);
                    return cfg;
                }))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/auth/**", "/api/storefront/**",
                                "/actuator/health", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        // Payment gateway IPN/callbacks are server-to-server; verified by signature inside the handler.
                        .requestMatchers(HttpMethod.POST, "/api/payments/callback/**").permitAll()
                        // Public storefront endpoints: shopper signup/login and order tracking.
                        .requestMatchers(HttpMethod.POST, "/stores/*/account/signup", "/stores/*/account/login",
                                "/stores/*/account/google", "/stores/*/track").permitAll()
                        // Shopper self-service phone OTP (rate-limited).
                        .requestMatchers(HttpMethod.POST, "/stores/*/account/phone/send-otp",
                                "/stores/*/account/phone/verify-otp").permitAll()
                        // Public checkout: order creation + gateway payment intents (rate-limited).
                        .requestMatchers(HttpMethod.POST, "/stores/*/checkout/orders",
                                "/stores/*/payments/intents").permitAll()
                        // Public "Back in Stock" alert subscription (rate-limited).
                        .requestMatchers(HttpMethod.POST, "/stores/*/stock-alerts").permitAll()
                        // Public contact form.
                        .requestMatchers(HttpMethod.POST, "/contact").permitAll()
                        // Public landing page content (rate-limited).
                        .requestMatchers(HttpMethod.GET, "/platform/landing").permitAll()
                        // Public demo theme previews (rate-limited).
                        .requestMatchers(HttpMethod.GET, "/platform/demo-themes").permitAll()
                        // Public store subdomain availability check (signup page, anonymous).
                        .requestMatchers(HttpMethod.GET, "/stores/available", "/api/stores/available").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantFilter, JwtAuthFilter.class)
                // API-key auth is the fallback: it only acts when no JWT
                // authentication exists, and runs after TenantFilter so the
                // key's store binding is the final TenantContext.
                .addFilterAfter(apiKeyAuthFilter, TenantFilter.class);
        return http.build();
    }
}
