package com.supportai.ai.controller;

import com.supportai.ai.TicketCategoryClassifier;
import com.supportai.ai.TicketPriorityClassifier;
import com.supportai.ai.dto.ClassifyTicketRequest;
import com.supportai.ai.dto.ClassifyTicketResponse;
import com.supportai.ai.dto.SuggestTicketPriorityRequest;
import com.supportai.ai.dto.SuggestTicketPriorityResponse;
import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.entity.TicketPriority;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final TicketCategoryClassifier categoryClassifier;
    private final TicketPriorityClassifier priorityClassifier;

    public AiController(
            TicketCategoryClassifier categoryClassifier,
            TicketPriorityClassifier priorityClassifier
    ) {
        this.categoryClassifier = categoryClassifier;
        this.priorityClassifier = priorityClassifier;
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

    @PostMapping("/suggest-priority")
    public SuggestTicketPriorityResponse suggestPriority(
            @Valid @RequestBody SuggestTicketPriorityRequest request
    ) {
        TicketPriority priority = priorityClassifier.classifyPriority(
                request.title(),
                request.description()
        );

        return new SuggestTicketPriorityResponse(priority);
    }
}
