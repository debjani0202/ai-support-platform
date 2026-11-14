package com.supportai.ai;

import com.supportai.ticket.entity.TicketPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HybridTicketPriorityClassifierTest {

    private final RuleBasedTicketPriorityClassifier ruleBasedClassifier =
            mock(RuleBasedTicketPriorityClassifier.class);

    private final OllamaTicketPriorityClassifier ollamaClassifier =
            mock(OllamaTicketPriorityClassifier.class);

    private final HybridTicketPriorityClassifier hybridClassifier =
            new HybridTicketPriorityClassifier(
                    ruleBasedClassifier,
                    ollamaClassifier
            );

    @Test
    @DisplayName("Returns rule-based priority immediately without invoking Ollama when rule matches")
    void shouldUseRuleBasedPriorityWhenRuleMatches() {
        when(ruleBasedClassifier.classifyPriority(
                "Production is down",
                "Entire system is down."
        )).thenReturn(TicketPriority.URGENT);

        TicketPriority result = hybridClassifier.classifyPriority(
                "Production is down",
                "Entire system is down."
        );

        assertEquals(TicketPriority.URGENT, result);
        verify(ruleBasedClassifier).classifyPriority(
                "Production is down",
                "Entire system is down."
        );
        verifyNoInteractions(ollamaClassifier);
    }

    @Test
    @DisplayName("Delegates to Ollama when no rule matches")
    void shouldUseOllamaWhenNoRuleMatches() {
        when(ruleBasedClassifier.classifyPriority(
                "Complex report issue",
                "Certain filter combinations take longer to export."
        )).thenReturn(null);

        when(ollamaClassifier.classifyPriority(
                "Complex report issue",
                "Certain filter combinations take longer to export."
        )).thenReturn(TicketPriority.MEDIUM);

        TicketPriority result = hybridClassifier.classifyPriority(
                "Complex report issue",
                "Certain filter combinations take longer to export."
        );

        assertEquals(TicketPriority.MEDIUM, result);
        verify(ruleBasedClassifier).classifyPriority(
                "Complex report issue",
                "Certain filter combinations take longer to export."
        );
        verify(ollamaClassifier).classifyPriority(
                "Complex report issue",
                "Certain filter combinations take longer to export."
        );
    }

    @Test
    @DisplayName("Returns null gracefully when Ollama throws IllegalStateException")
    void returnsNullOnOllamaIllegalStateException() {
        when(ruleBasedClassifier.classifyPriority(
                "Some issue",
                "Some description"
        )).thenReturn(null);

        when(ollamaClassifier.classifyPriority(
                "Some issue",
                "Some description"
        )).thenThrow(new IllegalStateException("Ollama timeout"));

        TicketPriority result = hybridClassifier.classifyPriority(
                "Some issue",
                "Some description"
        );

        assertNull(result);
        verify(ruleBasedClassifier).classifyPriority("Some issue", "Some description");
        verify(ollamaClassifier).classifyPriority("Some issue", "Some description");
    }

    @Test
    @DisplayName("Returns null gracefully when Ollama capacity is reached")
    void returnsNullOnOllamaCapacityReached() {
        when(ruleBasedClassifier.classifyPriority("T", "D")).thenReturn(null);
        when(ollamaClassifier.classifyPriority("T", "D"))
                .thenThrow(new IllegalStateException("Ollama priority classification capacity reached"));

        TicketPriority result = hybridClassifier.classifyPriority("T", "D");

        assertNull(result);
    }
}
