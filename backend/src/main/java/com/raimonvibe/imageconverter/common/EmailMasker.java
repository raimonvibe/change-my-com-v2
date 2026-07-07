package com.raimonvibe.imageconverter.common;

/**
 * Masks email addresses for privacy-compliant logging.
 * Example: "robertjanstefan@gmail.com" becomes "r***@gmail.com".
 * Shared by CostMonitor and SecurityAuditLogger (previously duplicated in both).
 */
public final class EmailMasker {

    private EmailMasker() {}

    public static String mask(String email) {
        if (email == null || email.isEmpty()) {
            return "anonymous";
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        return localPart.charAt(0) + "***" + domain;
    }
}
