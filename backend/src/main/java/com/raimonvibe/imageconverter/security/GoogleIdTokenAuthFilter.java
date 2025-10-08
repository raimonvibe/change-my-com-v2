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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GoogleIdTokenAuthFilter extends OncePerRequestFilter {

  private final UserService userService;

  @Value("${app.auth.googleClientId:}")
  private String googleClientId;

  public GoogleIdTokenAuthFilter(UserService userService) {
    this.userService = userService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      System.out.println("=== GOOGLE OAUTH DEBUG ===");
      System.out.println("Request URI: " + request.getRequestURI());
      System.out.println("Token length: " + token.length());
      System.out.println("Expected Client ID: " + googleClientId);
      System.out.println("Token preview: " + token.substring(0, Math.min(20, token.length())) + "...");
      
      try {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(Collections.singletonList(googleClientId))
            .build();
        
        System.out.println("Attempting to verify token with Google...");
        GoogleIdToken idToken = verifier.verify(token);
        
        if (idToken != null) {
          String email = Optional.ofNullable(idToken.getPayload().getEmail()).orElse(null);
          System.out.println("Token validation SUCCESS for email: " + email);
          if (email != null) {
            var user = userService.ensureUserByEmail(email);
            var auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null, Collections.singleton(new SimpleGrantedAuthority("USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            System.out.println("Authentication set for user: " + user.getEmail());
          }
        } else {
          System.err.println("Token validation FAILED - idToken is null");
          System.err.println("This usually means:");
          System.err.println("1. Token is expired");
          System.err.println("2. Token audience doesn't match expected client ID");
          System.err.println("3. Token signature is invalid");
          System.err.println("4. Token is malformed");
        }
      } catch (Exception e) {
        System.err.println("Google token validation failed: " + e.getMessage());
        System.err.println("Exception type: " + e.getClass().getSimpleName());
        System.err.println("Full exception details:");
        e.printStackTrace();
      }
      System.out.println("=== END GOOGLE OAUTH DEBUG ===");
    } else {
      System.out.println("No Authorization header found for: " + request.getRequestURI());
    }
    chain.doFilter(request, response);
  }
}
