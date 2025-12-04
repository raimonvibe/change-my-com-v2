package com.raimonvibe.imageconverter.config;

import java.util.List;

import com.raimonvibe.imageconverter.security.RateLimitFilter;
import com.raimonvibe.imageconverter.security.GoogleIdTokenAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class SecurityConfig {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    // ====== Security Filter Chain ======
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, RateLimitFilter rateLimitFilter, GoogleIdTokenAuthFilter googleAuthFilter) throws Exception {
        http
            // Stateless API - explicitly configure session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // CSRF protection can be disabled for stateless APIs using token-based auth
            // No cookies/sessions are used, only Bearer tokens in Authorization header
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .headers(h -> h
                // Production-ready CSP
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'none'; " +
                    "img-src 'self' blob: data:; " +
                    "connect-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'self'; " +
                    "form-action 'self'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .xssProtection(x -> x.disable()) // CSP replaces XSS protection
                .contentTypeOptions(Customizer.withDefaults())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true)
                    .maxAgeInSeconds(31536000)) // 1 year
                // Additional security headers
                .addHeaderWriter((request, response) -> {
                    response.setHeader("Permissions-Policy", 
                        "camera=(), microphone=(), geolocation=(), payment=()");
                    response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
                    response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
                    response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Preflight altijd doorlaten
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Health open
                .requestMatchers("/health").permitAll()
                // Stripe webhook moet publiek zijn maar ondertekend (verificatie in controller!)
                .requestMatchers("/stripe/webhook").permitAll()
                // Stripe checkout: REQUIRES authentication to link subscription to user
                .requestMatchers(HttpMethod.POST, "/api/billing/checkout").authenticated()
                // Convert endpoints open (credits/limits in controller)
                .requestMatchers(HttpMethod.GET, "/api/convert/formats").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/convert").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/convert/gif").permitAll()
                // Debug endpoints: allow in dev, deny in prod
                .requestMatchers("/api/debug/**").access((authentication, context) -> 
                    new AuthorizationDecision("dev".equals(activeProfile) 
                        ? authentication.get().isAuthenticated() 
                        : false)
                )
                // Alles anders dicht tenzij je elders expliciet opent
                .anyRequest().authenticated()
            )
            // Google OAuth filter vóór rate limiting
            .addFilterBefore(googleAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // Rate limiting vóór auth/handlers
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ====== User Details Service ======
    /**
     * Disables UserDetailsServiceAutoConfiguration by providing a no-op implementation.
     * This application uses stateless JWT authentication via Google OAuth tokens only.
     * Username/password authentication is not supported.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(
                "This application uses JWT authentication only. Username/password login is not supported."
            );
        };
    }

    // ====== CORS ======
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        // Pas het prod-domein aan naar jouw echte frontend URL
        List<String> allowedOrigins = List.of(
            "http://localhost:3000",
            "https://www.change-my.com" // Production domain
        );

        CorsConfiguration conf = new CorsConfiguration();
        // Gebruik expliciete origins (geen "*") zodat browsers Authorization headers toestaan
        conf.setAllowedOrigins(allowedOrigins);
        conf.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        conf.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        // Bearer tokens via header → credentials niet nodig/uit
        conf.setAllowCredentials(false);
        // Expose rate-limit headers voor de frontend
        conf.setExposedHeaders(List.of("X-RateLimit-Remaining", "X-RateLimit-Reset", "Retry-After"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", conf);
        return source;
    }
}
