package com.raimonvibe.imageconverter.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.raimonvibe.imageconverter.user.UserService;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GoogleIdTokenAuthFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(GoogleIdTokenAuthFilter.class);

  private final UserService userService;
  private final GoogleIdTokenVerifier googleIdTokenVerifier;

  public GoogleIdTokenAuthFilter(
      UserService userService,
      GoogleIdTokenVerifier googleIdTokenVerifier) {
    this.userService = userService;
    this.googleIdTokenVerifier = googleIdTokenVerifier;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);

      if (logger.isDebugEnabled()) {
        logger.debug("Processing OAuth token for URI: {}", request.getRequestURI());
      }

      try {
        // Use singleton verifier bean (improves performance)
        GoogleIdToken idToken = googleIdTokenVerifier.verify(token);

        if (idToken != null) {
          String email = Optional.ofNullable(idToken.getPayload().getEmail()).orElse(null);
          if (email != null) {
            var user = userService.ensureUserByEmail(email);
            var auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null, Collections.singleton(new SimpleGrantedAuthority("USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            logger.info("Authentication successful for user ID: {}", user.getId());
          } else {
            logger.warn("Token validation succeeded but email is null");
          }
        } else {
          logger.warn("Token validation failed - invalid or expired token for URI: {}", request.getRequestURI());
        }
      } catch (Exception e) {
        logger.error("Google token validation error: {}", e.getMessage());
        if (logger.isDebugEnabled()) {
          logger.debug("Token validation exception details", e);
        }
      }
    }
    chain.doFilter(request, response);
  }
}
