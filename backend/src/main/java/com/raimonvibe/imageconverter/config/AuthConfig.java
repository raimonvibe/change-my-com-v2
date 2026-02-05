package com.raimonvibe.imageconverter.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * Authentication configuration for Google OAuth.
 * Creates singleton GoogleIdTokenVerifier to improve performance.
 */
@Configuration
public class AuthConfig {

    /**
     * Creates a singleton GoogleIdTokenVerifier bean.
     * Thread-safe and reuses the same HTTP client across all requests.
     *
     * @param googleClientId Google OAuth client ID from configuration
     * @return Configured and thread-safe GoogleIdTokenVerifier
     */
    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(
            @Value("${app.auth.googleClientId:}") String googleClientId) {

        if (googleClientId == null || googleClientId.isEmpty()) {
            throw new IllegalStateException(
                "Google Client ID not configured. Set app.auth.googleClientId in application.yml"
            );
        }

        return new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
            .setAudience(Collections.singletonList(googleClientId))
            .build();
    }
}
