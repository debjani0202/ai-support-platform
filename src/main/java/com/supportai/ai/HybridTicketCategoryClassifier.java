package com.supportai.ai;

import com.supportai.ticket.entity.TicketCategory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class HybridTicketCategoryClassifier implements TicketCategoryClassifier {

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

        return ollamaClassifier.classify(title, description);
    }
}