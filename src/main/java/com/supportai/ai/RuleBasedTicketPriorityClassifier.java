package com.supportai.ai;

import com.supportai.ticket.entity.TicketPriority;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Deterministic rule-based priority classifier.
 *
 * Provides conservative, fast-path matching for obvious and unambiguous priority levels:
 * - URGENT: Confirmed production-wide outages, widespread security incidents, or complete payment halts.
 * - HIGH: Major business functionality blocked or critical workflows failing without a complete site outage.
 * - LOW: Minor cosmetic issues, typographical corrections, or non-critical informational queries.
 *
 * Returns {@code null} for any ambiguous, complex, or unconfirmed cases so that the contextual
 * LLM (Ollama) can evaluate the ticket with full semantic awareness.
 */
@Component
public class RuleBasedTicketPriorityClassifier {

    public TicketPriority classifyPriority(String title, String description) {
        if (title == null && description == null) {
            return null;
        }

        String combined = ((title == null ? "" : title) + " "
                + (description == null ? "" : description)).trim();

        if (combined.isEmpty()) {
            return null;
        }

        String text = combined.toLowerCase(Locale.ROOT);

        // Guard against false positives: if the ticket describes a historical, resolved,
        // hypothetical, or explicitly negated incident, do not assign URGENT or HIGH via rules.
        // Let the LLM evaluate the contextual nuances instead.
        if (isHypotheticalOrResolvedOrNegated(text)) {
            // Still check if it is clearly a LOW priority question about a past/hypothetical incident
            if (isLowPriority(text)) {
                return TicketPriority.LOW;
            }
            return null;
        }

        // 1. URGENT: Widespread outages, total business failure, or critical active security threats
        if (isUrgentPriority(text)) {
            return TicketPriority.URGENT;
        }

        // 2. HIGH: Major business functionality impaired or critical workflow blocked
        if (isHighPriority(text)) {
            return TicketPriority.HIGH;
        }

        // 3. LOW: Cosmetic glitches, typos, minor documentation, or basic how-to queries
        if (isLowPriority(text)) {
            return TicketPriority.LOW;
        }

        // Return null for all remaining cases (do NOT default to MEDIUM).
        // Returning null allows the hybrid classifier to delegate to Ollama for context evaluation.
        return null;
    }

    private boolean isHypotheticalOrResolvedOrNegated(String text) {
        return containsAny(text,
                "was resolved", "has been resolved", "is resolved", "now resolved",
                "was fixed", "is fixed", "already fixed", "already resolved", "now fixed",
                "yesterday's outage", "last week's outage", "previous outage", "past outage", "prior outage",
                "not down", "not an outage", "no outage", "not a security incident", "not corrupted",
                "in case of outage", "in case of an outage", "what if production",
                "how to handle an outage", "how to handle production down",
                "outage drill", "disaster recovery drill", "simulation");
    }

    private boolean isUrgentPriority(String text) {
        return containsAny(text,
                // Production & system-wide outages
                "production is down", "production is completely down", "prod is down",
                "entire system is down", "system-wide outage", "site-wide outage",
                "complete system outage", "all services are down", "total system outage",
                "global outage across production",

                // Database corruption
                "database corruption in production", "database is corrupted",
                "data corruption in production", "production database corrupted",

                // Active security breach
                "active security breach", "security exploit in production",
                "active data breach", "ransomware attack on production",
                "unauthorized access to all customer data",

                // Complete payment halt
                "all payment transactions failing", "all payments are failing",
                "zero payments going through", "cannot process any payments",
                "payment processing completely down"
        );
    }

    private boolean isHighPriority(String text) {
        return containsAny(text,
                // Major functionality broken
                "checkout is broken", "payment gateway is down",
                "credit card processing failing", "users cannot checkout",
                "users cannot complete purchase", "payment gateway is failing",

                // Critical workflow crashes
                "app crashes on startup", "crashes immediately on launch",
                "crashes repeatedly on startup", "crash prevents users from checkout",

                // Blocked core operations
                "cannot submit tax reports", "month-end close blocked",
                "payroll processing blocked", "blocking our release",

                // Admin / agent access impairment
                "admin portal completely inaccessible", "all agents locked out"
        );
    }

    private boolean isLowPriority(String text) {
        return containsAny(text,
                // Typos & grammar
                "typo in documentation", "spelling error in", "grammatical error in",
                "misspelled word on", "label typo", "typo on button",

                // Cosmetic UI issues
                "cosmetic issue", "button alignment is off", "icon color is wrong",
                "misaligned banner", "padding issue on", "dark mode color contrast",
                "font size slightly off", "logo is slightly pixelated",

                // Documentation issues
                "broken link in documentation", "docs link is broken",
                "minor documentation correction", "update documentation link",

                // Informational how-to queries
                "where can i find the tutorial", "how to change profile avatar",
                "where is the user guide", "how do i change my profile picture"
        );
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
