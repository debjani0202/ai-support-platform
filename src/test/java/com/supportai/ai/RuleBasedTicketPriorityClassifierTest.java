package com.supportai.ai;

import com.supportai.ticket.entity.TicketPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuleBasedTicketPriorityClassifierTest {

    private final RuleBasedTicketPriorityClassifier classifier =
            new RuleBasedTicketPriorityClassifier();

    // --- URGENT Priority Tests ---

    @Test
    @DisplayName("Classifies active production-wide outage as URGENT")
    void classifiesProductionOutageAsUrgent() {
        assertEquals(
                TicketPriority.URGENT,
                classifier.classifyPriority(
                        "Production is down",
                        "The entire system is down and all customer requests are failing."
                )
        );

        assertEquals(
                TicketPriority.URGENT,
                classifier.classifyPriority(
                        "Critical emergency",
                        "We are experiencing a system-wide outage across all regions."
                )
        );

        assertEquals(
                TicketPriority.URGENT,
                classifier.classifyPriority(
                        "Prod is down",
                        "All services are down following the latest deploy."
                )
        );
    }

    @Test
    @DisplayName("Classifies confirmed database corruption as URGENT")
    void classifiesDatabaseCorruptionAsUrgent() {
        assertEquals(
                TicketPriority.URGENT,
                classifier.classifyPriority(
                        "Data integrity issue",
                        "We detected database corruption in production on user records."
                )
        );

        assertEquals(
                TicketPriority.URGENT,
                classifier.classifyPriority(
                        "Severe DB alert",
                        "Production database corrupted after unclean shutdown."
                )
        );
    }

    @Test
    @DisplayName("Classifies active security breach as URGENT")
    void classifiesSecurityBreachAsUrgent() {
        assertEquals(
                TicketPriority.URGENT,
                classifier.classifyPriority(
                        "Security alert",
                        "Active security breach identified on primary API gateway."
                )
        );

        assertEquals(
                TicketPriority.URGENT,
                classifier.classifyPriority(
                        "Urgent threat",
                        "Ransomware attack on production infrastructure detected."
                )
        );
    }

    @Test
    @DisplayName("Classifies complete failure of all payment transactions as URGENT")
    void classifiesTotalPaymentHaltAsUrgent() {
        assertEquals(
                TicketPriority.URGENT,
                classifier.classifyPriority(
                        "Billing pipeline halted",
                        "All payment transactions failing across our entire checkout."
                )
        );

        assertEquals(
                TicketPriority.URGENT,
                classifier.classifyPriority(
                        "Zero payments processing",
                        "Cannot process any payments since 9am this morning."
                )
        );
    }

    // --- HIGH Priority Tests ---

    @Test
    @DisplayName("Classifies major payment failure without complete outage as HIGH")
    void classifiesMajorPaymentFailureAsHigh() {
        assertEquals(
                TicketPriority.HIGH,
                classifier.classifyPriority(
                        "Payment issue",
                        "Checkout is broken for PayPal users while Stripe still works."
                )
        );

        assertEquals(
                TicketPriority.HIGH,
                classifier.classifyPriority(
                        "Card processing",
                        "Credit card processing failing on recurring subscriptions."
                )
        );
    }

    @Test
    @DisplayName("Classifies application crashes on startup as HIGH")
    void classifiesStartupCrashAsHigh() {
        assertEquals(
                TicketPriority.HIGH,
                classifier.classifyPriority(
                        "Crash on launch",
                        "App crashes on startup for all Android 14 devices."
                )
        );

        assertEquals(
                TicketPriority.HIGH,
                classifier.classifyPriority(
                        "Immediate crash",
                        "Client crashes immediately on launch when opening workspace."
                )
        );
    }

    @Test
    @DisplayName("Classifies blocked critical business workflows as HIGH")
    void classifiesBlockedCriticalWorkflowAsHigh() {
        assertEquals(
                TicketPriority.HIGH,
                classifier.classifyPriority(
                        "Accounting deadline",
                        "Month-end close blocked because export service produces empty files."
                )
        );

        assertEquals(
                TicketPriority.HIGH,
                classifier.classifyPriority(
                        "Payroll",
                        "Payroll processing blocked for bi-weekly disbursement."
                )
        );
    }

    @Test
    @DisplayName("Classifies admin access lockout as HIGH")
    void classifiesAdminLockoutAsHigh() {
        assertEquals(
                TicketPriority.HIGH,
                classifier.classifyPriority(
                        "Admin issue",
                        "Admin portal completely inaccessible for support team."
                )
        );
    }

    // --- LOW Priority Tests ---

    @Test
    @DisplayName("Classifies documentation typos and grammar issues as LOW")
    void classifiesTypoAsLow() {
        assertEquals(
                TicketPriority.LOW,
                classifier.classifyPriority(
                        "Doc update",
                        "Found a typo in documentation on the getting started page."
                )
        );

        assertEquals(
                TicketPriority.LOW,
                classifier.classifyPriority(
                        "Spelling",
                        "There is a spelling error in the welcome email footer."
                )
        );
    }

    @Test
    @DisplayName("Classifies cosmetic UI and styling issues as LOW")
    void classifiesCosmeticIssueAsLow() {
        assertEquals(
                TicketPriority.LOW,
                classifier.classifyPriority(
                        "Button alignment",
                        "The button alignment is off on wide monitor displays."
                )
        );

        assertEquals(
                TicketPriority.LOW,
                classifier.classifyPriority(
                        "Cosmetic bug",
                        "Icon color is wrong in dark mode color contrast."
                )
        );
    }

    @Test
    @DisplayName("Classifies informational how-to queries as LOW")
    void classifiesInformationalQueryAsLow() {
        assertEquals(
                TicketPriority.LOW,
                classifier.classifyPriority(
                        "User avatar",
                        "How do i change my profile picture in the dashboard?"
                )
        );

        assertEquals(
                TicketPriority.LOW,
                classifier.classifyPriority(
                        "Docs inquiry",
                        "Where can i find the tutorial for batch importing data?"
                )
        );
    }

    // --- Guard & Negation Tests (Avoid False Positives) ---

    @Test
    @DisplayName("Historical and resolved incidents do NOT incorrectly become URGENT or HIGH")
    void historicalOrResolvedIncidentsDoNotBecomeUrgent() {
        assertNull(
                classifier.classifyPriority(
                        "Incident post-mortem",
                        "Yesterday's outage is resolved and we need an incident report."
                )
        );

        assertNull(
                classifier.classifyPriority(
                        "Follow up",
                        "Production was down last week but was resolved by devops."
                )
        );

        assertNull(
                classifier.classifyPriority(
                        "Database check",
                        "The database corruption in production was fixed this morning."
                )
        );
    }

    @Test
    @DisplayName("Negated statements do NOT trigger false URGENT or HIGH classifications")
    void negatedStatementsDoNotTriggerUrgent() {
        assertNull(
                classifier.classifyPriority(
                        "Status verification",
                        "Confirming that production is not down after deployment."
                )
        );

        assertNull(
                classifier.classifyPriority(
                        "Monitoring check",
                        "No outage observed during yesterday's maintenance window."
                )
        );
    }

    @Test
    @DisplayName("Hypothetical questions and drills do NOT become URGENT")
    void hypotheticalQuestionsDoNotBecomeUrgent() {
        assertNull(
                classifier.classifyPriority(
                        "Disaster recovery planning",
                        "In case of outage, where is the runbook stored?"
                )
        );

        assertNull(
                classifier.classifyPriority(
                        "Architecture review",
                        "What if production goes down during failover testing?"
                )
        );

        assertNull(
                classifier.classifyPriority(
                        "Schedule",
                        "Conducting an outage drill for the site reliability team tomorrow."
                )
        );
    }

    @Test
    @DisplayName("Isolated keywords alone do NOT trigger false positives")
    void isolatedKeywordsDoNotTriggerFalsePositives() {
        assertNull(
                classifier.classifyPriority(
                        "Navigation issue",
                        "When scrolling down the table, rows jump unexpectedly."
                )
        );

        assertNull(
                classifier.classifyPriority(
                        "File export",
                        "Corruption of text file formatting occurred in local CSV export."
                )
        );
    }

    @Test
    @DisplayName("Ambiguous tickets return null to delegate to Ollama")
    void ambiguousTicketsReturnNull() {
        assertNull(
                classifier.classifyPriority(
                        "Need help",
                        "Can someone assist our team with general settings?"
                )
        );

        assertNull(
                classifier.classifyPriority(
                        "Feature enhancement",
                        "We would like to request an export button on the invoice list."
                )
        );

        assertNull(
                classifier.classifyPriority(
                        "Slow reports",
                        "The monthly report took about 15 seconds to download."
                )
        );
    }

    // --- Boundary and Null Input Tests ---

    @Test
    @DisplayName("Null or empty inputs safely return null")
    void nullOrEmptyInputsReturnNull() {
        assertNull(classifier.classifyPriority(null, null));
        assertNull(classifier.classifyPriority("", ""));
        assertNull(classifier.classifyPriority("   ", "   "));
        assertNull(classifier.classifyPriority(null, "Just a description"));
        assertNull(classifier.classifyPriority("Just a title", null));
    }
}
