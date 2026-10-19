package com.supportai.ai;

import com.supportai.ticket.entity.TicketCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class HybridTicketCategoryClassifierTest {

    private final RuleBasedTicketCategoryClassifier ruleBasedClassifier =
            mock(RuleBasedTicketCategoryClassifier.class);

    private final OllamaTicketCategoryClassifier ollamaClassifier =
            mock(OllamaTicketCategoryClassifier.class);

    private final HybridTicketCategoryClassifier hybridClassifier =
            new HybridTicketCategoryClassifier(
                    ruleBasedClassifier,
                    ollamaClassifier
            );

    @Test
    void shouldUseRuleBasedCategoryWhenRuleMatches() {
        when(ruleBasedClassifier.classify(
                "Cannot log in",
                "I cannot access my account."
        )).thenReturn(TicketCategory.ACCOUNT_ACCESS);

        TicketCategory result = hybridClassifier.classify(
                "Cannot log in",
                "I cannot access my account."
        );

        assertEquals(TicketCategory.ACCOUNT_ACCESS, result);
        verify(ruleBasedClassifier).classify(
                "Cannot log in",
                "I cannot access my account."
        );
        verifyNoInteractions(ollamaClassifier);
    }

    @Test
    void shouldUseOllamaWhenNoRuleMatches() {
        when(ruleBasedClassifier.classify(
                "Question about support",
                "What are your support hours?"
        )).thenReturn(null);

        when(ollamaClassifier.classify(
                "Question about support",
                "What are your support hours?"
        )).thenReturn(TicketCategory.GENERAL_QUERY);

        TicketCategory result = hybridClassifier.classify(
                "Question about support",
                "What are your support hours?"
        );

        assertEquals(TicketCategory.GENERAL_QUERY, result);
        verify(ollamaClassifier).classify(
                "Question about support",
                "What are your support hours?"
        );
    }
}
