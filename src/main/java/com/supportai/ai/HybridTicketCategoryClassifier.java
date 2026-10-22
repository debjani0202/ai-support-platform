package com.supportai.ai;

import com.supportai.ticket.entity.TicketCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class HybridTicketCategoryClassifier implements TicketCategoryClassifier {

    private static final Logger logger =
            LoggerFactory.getLogger(HybridTicketCategoryClassifier.class);

    private final RuleBasedTicketCategoryClassifier ruleBasedClassifier;
    private final OllamaTicketCategoryClassifier ollamaClassifier;

    public HybridTicketCategoryClassifier(
            RuleBasedTicketCategoryClassifier ruleBasedClassifier,
            OllamaTicketCategoryClassifier ollamaClassifier) {
        this.ruleBasedClassifier = ruleBasedClassifier;
        this.ollamaClassifier = ollamaClassifier;
    }

    @Override
    public TicketCategory classify(String title, String description) {
        TicketCategory category =
                ruleBasedClassifier.classify(title, description);

        if (category != null) {
            return category;
        }

        try {
            return ollamaClassifier.classify(title, description);
        } catch (RuntimeException exception) {
            logger.warn(
                    "AI ticket classification failed; leaving category unclassified. Error type: {}",
                    exception.getClass().getSimpleName()
            );
            return null;
        }
    }
}
