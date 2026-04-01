package com.raimonvibe.imageconverter.config;

import com.raimonvibe.imageconverter.billing.WebhookRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final WebhookRateLimitInterceptor webhookRateLimitInterceptor;

    public WebConfig(WebhookRateLimitInterceptor webhookRateLimitInterceptor) {
        this.webhookRateLimitInterceptor = webhookRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Apply rate limiting only to webhook endpoints
        registry.addInterceptor(webhookRateLimitInterceptor)
                .addPathPatterns("/stripe/webhook");
    }
}
