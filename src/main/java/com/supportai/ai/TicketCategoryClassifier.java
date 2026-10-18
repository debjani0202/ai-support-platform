package com.supportai.ai;

import com.supportai.ticket.entity.TicketCategory;

//Any class that acts as a ticket category classifier must provide a method that accepts a ticket title and description and returns a ticket category.

public interface TicketCategoryClassifier {

    TicketCategory classify(String title, String description);  //accepts the ticket's title and description.

}
