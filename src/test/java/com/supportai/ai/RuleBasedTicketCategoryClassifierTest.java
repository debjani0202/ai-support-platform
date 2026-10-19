package com.supportai.ai;

import com.supportai.ticket.entity.TicketCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuleBasedTicketCategoryClassifierTest {

    private final RuleBasedTicketCategoryClassifier classifier =
            new RuleBasedTicketCategoryClassifier();

    @Test
    void classifiesLoginProblemsAsAccountAccess() {
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                classifier.classify(
                        "Cannot log in",
                        "I cannot access my account."
                )
        );
    }

    @Test
    void classifiesDuplicateChargesAsPayment() {
        assertEquals(
                TicketCategory.PAYMENT,
                classifier.classify(
                        "Payment issue",
                        "My card was charged twice."
                )
        );
    }

    @Test
    void classifiesCrashesAsBugReport() {
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify(
                        "Application crashes",
                        "The application crashes when uploading a picture."
                )
        );
    }

    @Test
    void classifiesFeatureRequests() {
        assertEquals(
                TicketCategory.FEATURE_REQUEST,
                classifier.classify(
                        "Dark mode",
                        "Please add dark mode to the application."
                )
        );
    }

    @Test
    void returnsNullWhenNoRuleMatches() {
        assertNull(
                classifier.classify(
                        "Question about support",
                        "What are your support hours?"
                )
        );
    }
}