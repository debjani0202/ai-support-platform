package com.supportai.ai;

import com.supportai.ticket.entity.TicketCategory;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class RuleBasedTicketCategoryClassifier {

    public TicketCategory classify(String title, String description) {
        String text = ((title == null ? "" : title) + " "
                + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);

        if (containsAny(text,
                "cannot log in", "can't log in", "unable to log in",
                "login failed", "password reset", "forgot password",
                "account locked", "authentication failed")) {
            return TicketCategory.ACCOUNT_ACCESS;
        }

        if (containsAny(text,
                "charged twice", "double charge", "payment failed",
                "payment deducted", "refund", "billing issue",
                "subscription charge")) {
            return TicketCategory.PAYMENT;
        }

        if (containsAny(text,
                "app crashes", "application crashes", "keeps crashing",
                "unexpected exception", "software bug",
                "broken feature", "throws an exception")) {
            return TicketCategory.BUG_REPORT;
        }

        if (containsAny(text,
                "feature request", "please add", "add dark mode",
                "would like a feature", "new functionality")) {
            return TicketCategory.FEATURE_REQUEST;
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