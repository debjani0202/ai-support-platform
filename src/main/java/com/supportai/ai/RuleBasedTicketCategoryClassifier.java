package com.supportai.ai;

import com.supportai.ticket.entity.TicketCategory;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class RuleBasedTicketCategoryClassifier {

    public TicketCategory classify(String title, String description) {
        if (title == null && description == null) {
            return null;
        }

        String combined = ((title == null ? "" : title) + " "
                + (description == null ? "" : description)).trim();

        if (combined.isEmpty()) {
            return null;
        }

        String text = combined.toLowerCase(Locale.ROOT);

        // 1. ACCOUNT_ACCESS: Login, passwords, lockouts, authentication
        if (containsAny(text,
                "cannot log in", "can't log in", "unable to log in",
                "login failed", "login problem", "sign in failed", "sign-in failed",
                "cannot sign in", "can't sign in", "unable to sign in",
                "password reset", "reset my password", "reset password", "forgot password", "forgot my password",
                "account locked", "account is locked", "locked out",
                "cannot access my account", "can't access my account", "unable to access my account",
                "lost access to my account", "regain access to my account",
                "authentication failed", "invalid credentials", "wrong password",
                "two-factor authentication", "2fa")) {
            return TicketCategory.ACCOUNT_ACCESS;
        }

        // 2. BUG_REPORT: Crashes, exceptions, defects (evaluated before payment to catch defect reports)
        if (containsAny(text,
                "app crashes", "application crashes", "keeps crashing", "crashes unexpectedly",
                "closes unexpectedly", "closed unexpectedly", "crashes when", "crash when",
                "causes the application to crash", "causes the app to crash", "causes a crash",
                "application to crash", "app to crash",
                "unexpected exception", "throws an exception", "software bug", "bug report",
                "screen bug", "displays the wrong amount",
                "broken feature", "nullpointerexception", "stack trace",
                "button not working", "button does not work")) {
            return TicketCategory.BUG_REPORT;
        }

        // 3. PAYMENT: Transactions, duplicate charges, refunds, billing
        if (containsAny(text,
                "charged twice", "double charge", "payment failed",
                "payment deducted", "deducted twice", "refund not received", "refund request",
                "request a refund", "requesting a refund", "refund inquiry", "refund status",
                "issue a refund", "process a refund", "processing a refund",
                "refund my money", "refund my payment", "get a refund", "need a refund", "want a refund",
                "billing issue", "billing problem", "subscription charge",
                "cancel subscription", "cancel my subscription", "subscription cancellation",
                "overcharged", "charged incorrectly", "charged the wrong amount", "charged wrong amount",
                "card declined", "payment declined",
                "payment unsuccessful", "payment error")) {
            return TicketCategory.PAYMENT;
        }

        // 4. TECHNICAL_ISSUE: Performance, latency, connectivity, server reachability
        if (containsAny(text,
                "slow dashboard", "takes 40 seconds to load", "takes too long to load",
                "slow performance", "loading very slow", "system is slow",
                "performance issue", "extremely slow",
                "cannot connect to the server", "can't connect to server", "cannot connect to server",
                "connection problem", "connection timeout", "connection timed out",
                "connection failed", "network error",
                "server down", "server is down", "server unreachable", "service unavailable", "site is down")) {
            return TicketCategory.TECHNICAL_ISSUE;
        }

        // 5. FEATURE_REQUEST: Requests for new functionality or explicit improvements
        if (containsAny(text,
                "feature request", "please add", "could you add", "can you add",
                "add dark mode", "add a dark theme",
                "support for dark theme", "support for dark mode",
                "dark theme option", "dark mode option",
                "would like a feature", "would be great to have", "would love to have",
                "new functionality", "requesting a feature", "suggest a feature", "feature suggestion")) {
            return TicketCategory.FEATURE_REQUEST;
        }

        // 6. GENERAL_QUERY: General informational or how-to questions (evaluated last)
        if (containsAny(text,
                "how can i download", "how do i", "how can i", "how to", "how do we", "how can we",
                "where can i find", "where do i find", "where is the",
                "what are your support hours", "support hours", "support availability",
                "how to change profile", "how to update profile", "change profile picture",
                "user guide", "documentation on", "tutorial on")) {
            return TicketCategory.GENERAL_QUERY;
        }

        return null;
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