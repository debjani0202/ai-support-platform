package com.supportai.ai;

import com.supportai.ticket.entity.TicketPriority;

/**
 * Strategy interface for classifying or suggesting the priority of a support ticket
 * based on its title and description.
 */
public interface TicketPriorityClassifier {

    /**
     * Suggests a priority for the given ticket draft.
     *
     * @param title the ticket draft title
     * @param description the ticket draft detailed description
     * @return the suggested {@link TicketPriority}, or {@code null} if no confident suggestion can be made
     */
    TicketPriority classifyPriority(String title, String description);
}
