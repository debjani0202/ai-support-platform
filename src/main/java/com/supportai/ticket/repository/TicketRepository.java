package com.supportai.ticket.repository;

import com.supportai.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TicketRepository
        extends JpaRepository<Ticket, Long>,
                JpaSpecificationExecutor<Ticket> {
}
