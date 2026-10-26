package com.supportai.ai;

import com.supportai.ticket.entity.TicketCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuleBasedTicketCategoryClassifierTest {

    private final RuleBasedTicketCategoryClassifier classifier =
            new RuleBasedTicketCategoryClassifier();

    // --- Category Tests ---

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
    void classifiesAccountAccessWithVariousPhrases() {
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                classifier.classify("Password help", "I need to reset my password immediately.")
        );
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                classifier.classify("Forgot password", "Where do I go if I forgot my password?")
        );
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                classifier.classify("Sign in problem", "Unable to sign in due to invalid credentials.")
        );
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                classifier.classify("Locked out", "My account is locked after 3 attempts.")
        );
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                classifier.classify("MFA failure", "My two-factor authentication code was not received.")
        );
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                classifier.classify("Access recovery", "I lost access to my account after updating my phone.")
        );
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                classifier.classify("Account help", "How can I regain access to my account?")
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
    void classifiesBugReportsWithVariousPhrases() {
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify("Fatal error", "Application closed unexpectedly during export.")
        );
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify("App defect", "There is a software bug causing a nullpointerexception.")
        );
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify("Broken UI", "The submit button not working on checkout.")
        );
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify("Crash report", "The application keeps crashing every 5 minutes.")
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
    void classifiesPaymentIssuesWithVariousPhrases() {
        assertEquals(
                TicketCategory.PAYMENT,
                classifier.classify("Bank statement", "My payment was deducted twice for one order.")
        );
        assertEquals(
                TicketCategory.PAYMENT,
                classifier.classify("Refund inquiry", "My refund request has not been processed yet.")
        );
        assertEquals(
                TicketCategory.PAYMENT,
                classifier.classify("Subscription", "I want to cancel my subscription.")
        );
        assertEquals(
                TicketCategory.PAYMENT,
                classifier.classify("Declined card", "My card declined during checkout.")
        );
        assertEquals(
                TicketCategory.PAYMENT,
                classifier.classify("Billing inquiry", "I was overcharged on my last statement.")
        );
    }

    @Test
    void classifiesTechnicalIssuesWithVariousPhrases() {
        assertEquals(
                TicketCategory.TECHNICAL_ISSUE,
                classifier.classify("Slow dashboard", "The dashboard takes 40 seconds to load.")
        );
        assertEquals(
                TicketCategory.TECHNICAL_ISSUE,
                classifier.classify("Connectivity", "The application cannot connect to the server.")
        );
        assertEquals(
                TicketCategory.TECHNICAL_ISSUE,
                classifier.classify("Latency", "System is slow and takes too long to load.")
        );
        assertEquals(
                TicketCategory.TECHNICAL_ISSUE,
                classifier.classify("Network error", "Connection timed out when fetching reports.")
        );
        assertEquals(
                TicketCategory.TECHNICAL_ISSUE,
                classifier.classify("Server status", "Looks like the server is down or unreachable.")
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
    void classifiesFeatureRequestsWithVariousPhrases() {
        assertEquals(
                TicketCategory.FEATURE_REQUEST,
                classifier.classify("Theme option", "Could you add a dark theme?")
        );
        assertEquals(
                TicketCategory.FEATURE_REQUEST,
                classifier.classify("Export feature", "Would be great to have CSV export functionality.")
        );
        assertEquals(
                TicketCategory.FEATURE_REQUEST,
                classifier.classify("Suggestion", "Requesting a feature to filter tickets by tag.")
        );
        assertEquals(
                TicketCategory.FEATURE_REQUEST,
                classifier.classify("Improvement", "Can you add multi-file upload support?")
        );
    }

    @Test
    void classifiesGeneralQueriesWithVariousPhrases() {
        assertEquals(
                TicketCategory.GENERAL_QUERY,
                classifier.classify("Monthly report", "How can I download my monthly report?")
        );
        assertEquals(
                TicketCategory.GENERAL_QUERY,
                classifier.classify("Help needed", "Where can I find the user settings?")
        );
        assertEquals(
                TicketCategory.GENERAL_QUERY,
                classifier.classify("Hours of operation", "What are your support hours?")
        );
        assertEquals(
                TicketCategory.GENERAL_QUERY,
                classifier.classify("Profile update", "Change profile picture on my user page.")
        );
        assertEquals(
                TicketCategory.GENERAL_QUERY,
                classifier.classify("Documentation", "Where is the user guide for new employees?")
        );
    }

    // --- Precedence & Overlap Tests ---

    @Test
    void bugReportTakesPrecedenceOverPaymentWhenCrashOccursDuringRefund() {
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify(
                        "App crash",
                        "The app crashes when processing a refund."
                )
        );
    }

    @Test
    void bugReportTakesPrecedenceOverPaymentWhenDefectMentionsPayment() {
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify(
                        "Software bug in checkout",
                        "Payment failed due to an unexpected exception and software bug."
                )
        );
    }

    @Test
    void refundScreenBugShouldBeClassifiedAsBugReportNotPayment() {
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify(
                        "Refund screen bug",
                        "The refund screen displays the wrong amount."
                )
        );
    }

    @Test
    void classifiesRequestForDarkThemeAsFeatureRequest() {
        assertEquals(
                TicketCategory.FEATURE_REQUEST,
                classifier.classify(
                        "Theme Request",
                        "Could you add a dark theme?"
                )
        );
        assertEquals(
                TicketCategory.FEATURE_REQUEST,
                classifier.classify(
                        "Could you add a dark theme?",
                        null
                )
        );
    }

    @Test
    void classifiesDefectInDarkThemeAsBugReportNotFeatureRequest() {
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify(
                        "Dark theme defect",
                        "The dark theme causes the application to crash."
                )
        );
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify(
                        "The dark theme causes the application to crash.",
                        null
                )
        );
    }

    @Test
    void accountAccessTakesPrecedenceOverGeneralQuery() {
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                classifier.classify(
                        "Password Question",
                        "How do I reset my password?"
                )
        );
    }

    @Test
    void doesNotClassifyGeneralAccountQueryAsAccountAccess() {
        assertEquals(
                TicketCategory.GENERAL_QUERY,
                classifier.classify(
                        "Account settings",
                        "Where can I find how to access my account profile?"
                )
        );
        assertEquals(
                TicketCategory.GENERAL_QUERY,
                classifier.classify(
                        "Help",
                        "How do I access my account settings?"
                )
        );
    }

    @Test
    void classifiesIncorrectChargeAmountAsPaymentNotBugReport() {
        assertEquals(
                TicketCategory.PAYMENT,
                classifier.classify(
                        "Billing statement",
                        "My card was charged the wrong amount for subscription."
                )
        );
    }

    @Test
    void paymentTakesPrecedenceOverGeneralQueryWhenAskingAboutRefund() {
        assertEquals(
                TicketCategory.PAYMENT,
                classifier.classify(
                        "Refund Question",
                        "How can I request a refund for a double charge?"
                )
        );
    }

    @Test
    void technicalIssueTakesPrecedenceOverGeneralQueryWhenAskingAboutConnectivity() {
        assertEquals(
                TicketCategory.TECHNICAL_ISSUE,
                classifier.classify(
                        "Connection Query",
                        "How to fix: cannot connect to the server?"
                )
        );
    }

    // --- Case Insensitivity Tests ---

    @Test
    void matchingIsCaseInsensitive() {
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                classifier.classify("CANNOT LOG IN", "MY ACCOUNT IS LOCKED")
        );
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify("APPLICATION CRASHES", "UNEXPECTED EXCEPTION OCCURRED")
        );
        assertEquals(
                TicketCategory.PAYMENT,
                classifier.classify("PAYMENT FAILED", "WAS CHARGED TWICE")
        );
        assertEquals(
                TicketCategory.TECHNICAL_ISSUE,
                classifier.classify("SLOW DASHBOARD", "TAKES 40 SECONDS TO LOAD")
        );
        assertEquals(
                TicketCategory.FEATURE_REQUEST,
                classifier.classify("COULD YOU ADD A DARK THEME?", "NEW FUNCTIONALITY REQUEST")
        );
        assertEquals(
                TicketCategory.GENERAL_QUERY,
                classifier.classify("HOW CAN I DOWNLOAD MY MONTHLY REPORT?", "WHERE CAN I FIND IT?")
        );
    }

    // --- Boundary, Null, and Blank Input Tests ---

    @Test
    void returnsNullWhenBothInputsAreNull() {
        assertNull(classifier.classify(null, null));
    }

    @Test
    void classifiesCorrectlyWhenTitleIsNull() {
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                classifier.classify(null, "Cannot log in to my account.")
        );
    }

    @Test
    void classifiesCorrectlyWhenDescriptionIsNull() {
        assertEquals(
                TicketCategory.BUG_REPORT,
                classifier.classify("Application crashes", null)
        );
    }

    @Test
    void returnsNullWhenInputsAreEmptyOrWhitespaceOnly() {
        assertNull(classifier.classify("", ""));
        assertNull(classifier.classify("   ", "   "));
        assertNull(classifier.classify("", "   "));
        assertNull(classifier.classify("   ", null));
        assertNull(classifier.classify(null, "   "));
    }

    // --- Unmatched Descriptions ---

    @Test
    void returnsNullWhenNoRuleMatches() {
        assertNull(
                classifier.classify(
                        "Question about policy",
                        "What is your company privacy policy?"
                )
        );
        assertNull(
                classifier.classify(
                        "General feedback",
                        "I used your app yesterday and it went smoothly."
                )
        );
    }

    @Test
    void doesNotClassifyIncorrectInvoiceCalculationAsPayment() {
        assertNull(
                classifier.classify(
                        "Incorrect total shown on my invoice",
                        "The application calculates my invoice total as 1200, but the correct total should be 1000."
                )
        );
    }
}
