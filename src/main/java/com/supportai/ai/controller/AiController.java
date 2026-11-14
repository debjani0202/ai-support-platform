package com.supportai.ai.controller;

import com.supportai.ai.TicketCategoryClassifier;
import com.supportai.ai.dto.ClassifyTicketRequest;
import com.supportai.ai.dto.ClassifyTicketResponse;
import com.supportai.ticket.entity.TicketCategory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final TicketCategoryClassifier categoryClassifier;

    public AiController(TicketCategoryClassifier categoryClassifier) {
        this.categoryClassifier = categoryClassifier;
    }

    @PostMapping("/classify")
    public ClassifyTicketResponse classify(
            @Valid @RequestBody ClassifyTicketRequest request
    ) {
        TicketCategory category = categoryClassifier.classify(
                request.title(),
                request.description()
        );

        return new ClassifyTicketResponse(category);
    }
}
