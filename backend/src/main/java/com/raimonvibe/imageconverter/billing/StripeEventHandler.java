package com.raimonvibe.imageconverter.billing;

import com.stripe.model.Event;

/**
 * Strategy for processing one Stripe webhook event type.
 * Implementations are Spring components; StripeWebhookController collects them
 * into a registry keyed by {@link #eventType()}, replacing the switch statement
 * that previously dispatched events. Adding support for a new event type only
 * requires a new implementation, not a controller change.
 */
public interface StripeEventHandler {

    /** The Stripe event type this handler processes, e.g. "invoice.paid". */
    String eventType();

    /**
     * Processes a verified, deduplicated event. Implementations must not throw
     * for bad event payloads; they log and return, matching webhook semantics
     * where Stripe should not retry events we have already recorded.
     */
    void handle(Event event);
}
