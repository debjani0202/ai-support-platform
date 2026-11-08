package com.supportai.ticket.repository;

import com.supportai.ticket.dto.TicketStatsResponse;
import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.entity.TicketPriority;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketRepositoryIntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    private User customer1;
    private User customer2;
    private User agent2;
    private User agent3;
    private User admin;

    @BeforeEach
    void setUp() {
        customer1 = userRepository.save(new User(
                "Customer One",
                "customer1_integration_test@supportai.com",
                "SecurePass123!",
                Role.CUSTOMER
        ));

        customer2 = userRepository.save(new User(
                "Customer Two",
                "customer2_integration_test@supportai.com",
                "SecurePass123!",
                Role.CUSTOMER
        ));

        agent2 = userRepository.save(new User(
                "Agent Two",
                "agent2_integration_test@supportai.com",
                "SecurePass123!",
                Role.AGENT
        ));

        agent3 = userRepository.save(new User(
                "Agent Three",
                "agent3_integration_test@supportai.com",
                "SecurePass123!",
                Role.AGENT
        ));

        admin = userRepository.save(new User(
                "Admin User",
                "admin_integration_test@supportai.com",
                "SecurePass123!",
                Role.ADMIN
        ));

        // 1. OPEN ticket created by Customer 1 and assigned to Agent 2
        createTicket("Open Ticket C1-A2", "Desc", "OPEN", customer1, agent2, null);

        // 2. IN_PROGRESS ticket created by Customer 1 and assigned to Agent 2
        createTicket("In Progress Ticket C1-A2", "Desc", "IN_PROGRESS", customer1, agent2, null);

        // 3. RESOLVED ticket created by Customer 1 and assigned to Agent 2
        createTicket("Resolved Ticket C1-A2", "Desc", "RESOLVED", customer1, agent2, null);

        // 4. CLOSED ticket created by Customer 1 and assigned to Agent 2
        createTicket("Closed Ticket C1-A2", "Desc", "CLOSED", customer1, agent2, null);

        // 5. Soft-deleted ticket created by Customer 1 and assigned to Agent 2
        createTicket("Deleted Ticket C1-A2", "Desc", "OPEN", customer1, agent2, LocalDateTime.now());

        // 6. OPEN ticket created by Customer 2 and assigned to Agent 3
        createTicket("Open Ticket C2-A3", "Desc", "OPEN", customer2, agent3, null);

        ticketRepository.flush();
    }

    private Ticket createTicket(
            String title,
            String description,
            String status,
            User createdBy,
            User assignedTo,
            LocalDateTime deletedAt
    ) {
        Ticket ticket = new Ticket(title, description, status);
        ticket.setCategory(TicketCategory.GENERAL_QUERY);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setCreatedBy(createdBy);
        ticket.setAssignedTo(assignedTo);
        ticket.setDeletedAt(deletedAt);
        return ticketRepository.save(ticket);
    }

    @Test
    @DisplayName("CUSTOMER stats query: returns only tickets created by customer, counts CLOSED in total, excludes soft-deleted")
    void getTicketStatsForCustomer_returnsCustomerCreatedCounts() {
        TicketStatsResponse stats = ticketRepository.getTicketStatsForCustomer(customer1.getId());

        assertNotNull(stats);
        assertEquals(4L, stats.total(), "Total should be 4 (OPEN + IN_PROGRESS + RESOLVED + CLOSED)");
        assertEquals(1L, stats.open(), "OPEN should be 1");
        assertEquals(1L, stats.inProgress(), "IN_PROGRESS should be 1");
        assertEquals(1L, stats.resolved(), "RESOLVED should be 1");
    }

    @Test
    @DisplayName("AGENT stats query: returns only tickets assigned to agent, counts CLOSED in total, excludes soft-deleted")
    void getTicketStatsForAgent_returnsAgentAssignedCounts() {
        TicketStatsResponse stats = ticketRepository.getTicketStatsForAgent(agent2.getId());

        assertNotNull(stats);
        assertEquals(4L, stats.total(), "Total should be 4 (OPEN + IN_PROGRESS + RESOLVED + CLOSED)");
        assertEquals(1L, stats.open(), "OPEN should be 1");
        assertEquals(1L, stats.inProgress(), "IN_PROGRESS should be 1");
        assertEquals(1L, stats.resolved(), "RESOLVED should be 1");
    }

    @Test
    @DisplayName("AGENT stats query: returns only tickets assigned to Agent 3, isolating from Agent 2's tickets")
    void getTicketStatsForAgent_agent3_returnsOnlyAssignedTickets() {
        TicketStatsResponse stats = ticketRepository.getTicketStatsForAgent(agent3.getId());

        assertNotNull(stats);
        assertEquals(1L, stats.total(), "Total should be 1");
        assertEquals(1L, stats.open(), "OPEN should be 1");
        assertEquals(0L, stats.inProgress(), "IN_PROGRESS should be 0");
        assertEquals(0L, stats.resolved(), "RESOLVED should be 0");
    }

    @Test
    @DisplayName("ADMIN stats query: returns platform-wide counts across all users, counts CLOSED in total, excludes soft-deleted")
    void getTicketStatsForAdmin_returnsPlatformWideCounts() {
        TicketStatsResponse stats = ticketRepository.getTicketStatsForAdmin();

        assertNotNull(stats);
        assertEquals(5L, stats.total(), "Total should be 5 (4 from Customer 1 + 1 from Customer 2)");
        assertEquals(2L, stats.open(), "OPEN should be 2 (1 from Customer 1 + 1 from Customer 2)");
        assertEquals(1L, stats.inProgress(), "IN_PROGRESS should be 1");
        assertEquals(1L, stats.resolved(), "RESOLVED should be 1");
    }

    @Test
    @DisplayName("CUSTOMER stats query: returns four zeroes when customer ID has no tickets")
    void getTicketStatsForCustomer_nonExistentId_returnsFourZeroes() {
        TicketStatsResponse stats = ticketRepository.getTicketStatsForCustomer(999999L);

        assertNotNull(stats);
        assertEquals(0L, stats.total());
        assertEquals(0L, stats.open());
        assertEquals(0L, stats.inProgress());
        assertEquals(0L, stats.resolved());
    }

    @Test
    @DisplayName("AGENT stats query: returns four zeroes when agent ID has no assigned tickets")
    void getTicketStatsForAgent_nonExistentId_returnsFourZeroes() {
        TicketStatsResponse stats = ticketRepository.getTicketStatsForAgent(999999L);

        assertNotNull(stats);
        assertEquals(0L, stats.total());
        assertEquals(0L, stats.open());
        assertEquals(0L, stats.inProgress());
        assertEquals(0L, stats.resolved());
    }

    @Test
    @DisplayName("ADMIN stats query: returns four zeroes when no tickets exist in database")
    void getTicketStatsForAdmin_emptyDatabase_returnsFourZeroes() {
        ticketRepository.deleteAll();
        ticketRepository.flush();

        TicketStatsResponse stats = ticketRepository.getTicketStatsForAdmin();

        assertNotNull(stats);
        assertEquals(0L, stats.total());
        assertEquals(0L, stats.open());
        assertEquals(0L, stats.inProgress());
        assertEquals(0L, stats.resolved());
    }
}

