package com.supportai.ai;

import com.supportai.ticket.entity.TicketPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary hybrid priority classifier orchestrating fast-path rule-based matching
 * and contextual LLM (Ollama) classification.
 *
 * Guarantees that AI failures or timeouts never throw exceptions, returning {@code null}
 * gracefully so that core ticket workflows remain uninterrupted.
 */
@Component
@Primary
public class HybridTicketPriorityClassifier implements TicketPriorityClassifier {

    private static final Logger logger =
            LoggerFactory.getLogger(HybridTicketPriorityClassifier.class);

    private final RuleBasedTicketPriorityClassifier ruleBasedClassifier;
    private final OllamaTicketPriorityClassifier ollamaClassifier;

    public HybridTicketPriorityClassifier(
            RuleBasedTicketPriorityClassifier ruleBasedClassifier,
            OllamaTicketPriorityClassifier ollamaClassifier) {
        this.ruleBasedClassifier = ruleBasedClassifier;
        this.ollamaClassifier = ollamaClassifier;
    }

    @Override
    public TicketPriority classifyPriority(String title, String description) {
        TicketPriority priority =
                ruleBasedClassifier.classifyPriority(title, description);

        if (priority != null) {
            return priority;
        }

        try {
            return ollamaClassifier.classifyPriority(title, description);
        } catch (RuntimeException exception) {
            logger.warn(
                    "AI ticket priority classification failed; leaving priority unclassified. Reason: {} (cause: {})",
                    exception.getMessage(),
                    exception.getCause() != null
                            ? exception.getCause().getClass().getSimpleName()
                            : "none"
            );
            return null;
        }
    }
}
