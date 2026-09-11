package com.supportai.ticket.repository;

import com.supportai.ticket.entity.Ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Existing methods — required by the current TicketService

    Page<Ticket> findByStatusIgnoreCase(
            String status,
            Pageable pageable
    );

    Page<Ticket> findByTitleContainingIgnoreCase(
            String title,
            Pageable pageable
    );

    Page<Ticket> findByStatusIgnoreCaseAndTitleContainingIgnoreCase(
            String status,
            String title,
            Pageable pageable
    );

    // New methods — required for ticket ownership authorization

    Page<Ticket> findByCreatedById(
            Long userId,
            Pageable pageable
    );

    Page<Ticket> findByAssignedToId(
            Long userId,
            Pageable pageable
    );

    Page<Ticket> findByCreatedByIdAndStatusIgnoreCase(
            Long userId,
            String status,
            Pageable pageable
    );

    Page<Ticket> findByAssignedToIdAndStatusIgnoreCase(
            Long userId,
            String status,
            Pageable pageable
    );

    Page<Ticket> findByCreatedByIdAndTitleContainingIgnoreCase(
            Long userId,
            String title,
            Pageable pageable
    );

    Page<Ticket> findByAssignedToIdAndTitleContainingIgnoreCase(
            Long userId,
            String title,
            Pageable pageable
    );

    Page<Ticket> findByCreatedByIdAndStatusIgnoreCaseAndTitleContainingIgnoreCase(
            Long userId,
            String status,
            String title,
            Pageable pageable
    );

    Page<Ticket> findByAssignedToIdAndStatusIgnoreCaseAndTitleContainingIgnoreCase(
            Long userId,
            String status,
            String title,
            Pageable pageable
    );
}