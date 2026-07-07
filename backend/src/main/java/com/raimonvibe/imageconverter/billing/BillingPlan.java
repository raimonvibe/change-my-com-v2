package com.raimonvibe.imageconverter.billing;

/**
 * Single source of truth for the subscription plan wiring between checkout and
 * webhook processing. The metadata written by BillingController at checkout
 * must match what StripeWebhookController reads back, and the credit amount
 * granted on activation must match the amount added on each renewal.
 */
public final class BillingPlan {

    private BillingPlan() {}

    /** Stripe checkout metadata key identifying the purchased plan. */
    public static final String METADATA_KEY = "subscription";

    /** Identifier of the single monthly plan currently offered. */
    public static final String MONTHLY_PLAN_ID = "monthly_1000";

    /** Credits granted on subscription activation and on each monthly renewal. */
    public static final int MONTHLY_CREDITS = 1000;

    /** Product name shown on the Stripe checkout page. */
    public static final String PRODUCT_NAME = "1000 Conversions per Month";
}
