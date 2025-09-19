package com.raimonvibe.imageconverter.config;

import java.util.List;

import com.raimonvibe.imageconverter.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    // ====== Security Filter Chain ======
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, RateLimitFilter rateLimitFilter) throws Exception {
        http
            // Stateless API (geen cookies/sessies)
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .headers(h -> h
                // Strakke CSP voor een API (alleen connect/img/self; pas evt. aan voor OpenAPI-UI)
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'none'; " +
                    "img-src 'self' blob: data:; " +
                    "connect-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .xssProtection(x -> x.disable()) // vervangen door CSP
                .contentTypeOptions(Customizer.withDefaults())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true))
            )
            .authorizeHttpRequests(auth -> auth
                // Preflight altijd doorlaten
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Health open
                .requestMatchers("/health").permitAll()
                // Stripe webhook moet publiek zijn maar ondertekend (verificatie in controller!)
                .requestMatchers("/stripe/webhook").permitAll()
                // Stripe checkout aanroep vanaf frontend toestaan
                .requestMatchers(HttpMethod.POST, "/api/billing/checkout").permitAll()
                // Convert endpoints open (credits/limits in controller)
                .requestMatchers(HttpMethod.GET, "/api/convert/formats").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/convert").permitAll()
                // Debug alleen tijdelijk in DEV — zet uit in PROD
                .requestMatchers("/api/debug/**").denyAll()
                // Alles anders dicht tenzij je elders expliciet opent
                .anyRequest().authenticated()
            )
            // Rate limiting vóór auth/handlers
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ====== CORS ======
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        // Pas het prod-domein aan naar jouw echte frontend URL
        List<String> allowedOrigins = List.of(
            "http://localhost:3000",
            "https://change-my-image.app" // <— vervang door jouw productie-frontend
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
