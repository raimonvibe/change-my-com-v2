// src/main/java/com/raimonvibe/imageconverter/billing/DebugController.java
package com.raimonvibe.imageconverter.billing;

import java.security.Principal;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/debug")
public class DebugController {
  @GetMapping("/whoami")
  public Map<String, Object> whoami(Principal principal, HttpServletRequest req) {
    return Map.of(
      "user", principal != null ? principal.getName() : null,
      "authType", req.getAuthType(),
      "hasAuthHeader", req.getHeader("Authorization") != null
    );
  }
}
