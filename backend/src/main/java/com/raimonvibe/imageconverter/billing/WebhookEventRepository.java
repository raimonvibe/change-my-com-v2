package com.raimonvibe.imageconverter.billing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    boolean existsByStripeEventId(String stripeEventId);
}
