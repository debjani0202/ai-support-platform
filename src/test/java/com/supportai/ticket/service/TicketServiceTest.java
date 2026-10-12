
package com.supportai.ticket.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.supportai.exception.ForbiddenException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.ticket.dto.TicketResponse;
import com.supportai.ticket.dto.UpdateTicketRequest;
import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.entity.TicketPriority;
import com.supportai.ticket.repository.TicketRepository;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketServiceTest {

        private TicketRepository ticketRepository;
        private UserRepository userRepository;
        private TicketService ticketService;

        @BeforeEach
        void setUp() {
                ticketRepository = mock(TicketRepository.class);
                userRepository = mock(UserRepository.class);

                ticketService = new TicketService(
                                ticketRepository,
                                userRepository);
        }

        @Test
        void customerCannotAccessAnotherCustomersTicket() {
                User customerA = new User(
                                "Customer A",
                                "customerA@example.com",
                                "password",
                                Role.CUSTOMER);

                User customerB = new User(
                                "Customer B",
                                "customerB@example.com",
                                "password",
                                Role.CUSTOMER);

                ReflectionTestUtils.setField(customerA, "id", 1L);
                ReflectionTestUtils.setField(customerB, "id", 2L);

                Ticket ticket = new Ticket(
                                "Login issue",
                                "Cannot log in",
                                "OPEN");

                ticket.setCreatedBy(customerA);

                when(ticketRepository.findById(100L))
                                .thenReturn(Optional.of(ticket));

                assertThrows(
                                ForbiddenException.class,
                                () -> ticketService.getTicketById(
                                                100L,
                                                2L,
                                                Role.CUSTOMER));
        }

        @Test
        void agentCannotAccessTicketAssignedToAnotherAgent() {
                User agentA = new User(
                                "Agent A",
                                "agentA@example.com",
                                "password",
                                Role.AGENT);

                User agentB = new User(
                                "Agent B",
                                "agentB@example.com",
                                "password",
                                Role.AGENT);

                ReflectionTestUtils.setField(agentA, "id", 3L);
                ReflectionTestUtils.setField(agentB, "id", 4L);

                Ticket ticket = new Ticket(
                                "Login issue",
                                "Cannot log in",
                                "OPEN");

                ticket.setAssignedTo(agentA);

                when(ticketRepository.findById(100L))
                                .thenReturn(Optional.of(ticket));

                assertThrows(
                                ForbiddenException.class,
                                () -> ticketService.getTicketById(
                                                100L,
                                                4L,
                                                Role.AGENT));
        }

        @Test
        void customerCannotChangeTicketStatus() {
                User customer = new User(
                                "Customer",
                                "customer@example.com",
                                "password",
                                Role.CUSTOMER);

                ReflectionTestUtils.setField(customer, "id", 1L);

                Ticket ticket = new Ticket(
                                "Login issue",
                                "Cannot log in",
                                "OPEN");

                ticket.setCreatedBy(customer);

                when(ticketRepository.findById(100L))
                                .thenReturn(Optional.of(ticket));

                UpdateTicketRequest request = new UpdateTicketRequest(
                                "Login issue",
                                "Cannot log in",
                                "RESOLVED",
                                TicketCategory.TECHNICAL_ISSUE,
                                TicketPriority.HIGH);

                assertThrows(
                                ForbiddenException.class,
                                () -> ticketService.updateTicket(
                                                100L,
                                                request,
                                                1L,
                                                Role.CUSTOMER));
        }

        @Test
        void adminCanAccessAnotherCustomersTicket() {
                User customer = new User(
                                "Customer",
                                "customer@example.com",
                                "password",
                                Role.CUSTOMER);

                ReflectionTestUtils.setField(customer, "id", 1L);

                Ticket ticket = new Ticket(
                                "Login issue",
                                "Cannot log in",
                                "OPEN");

                ticket.setCreatedBy(customer);

                when(ticketRepository.findById(100L))
                                .thenReturn(Optional.of(ticket));

                TicketResponse response = ticketService.getTicketById(
                                100L,
                                99L,
                                Role.ADMIN);

                assertNotNull(response);
                assertEquals("Login issue", response.title());
        }

        @Test
        void missingTicketThrowsResourceNotFoundException() {
                when(ticketRepository.findById(999L))
                                .thenReturn(Optional.empty());

                assertThrows(
                                ResourceNotFoundException.class,
                                () -> ticketService.getTicketById(
                                                999L,
                                                1L,
                                                Role.CUSTOMER));
        }

        @Test
        void customerCanAccessTheirOwnTicket() {
                User customer = new User(
                                "Customer",
                                "customer@example.com",
                                "password",
                                Role.CUSTOMER);

                ReflectionTestUtils.setField(customer, "id", 1L);

                Ticket ticket = new Ticket(
                                "Login issue",
                                "Cannot log in",
                                "OPEN");

                ticket.setCreatedBy(customer);

                when(ticketRepository.findById(100L))
                                .thenReturn(Optional.of(ticket));

                TicketResponse response = ticketService.getTicketById(
                                100L,
                                1L,
                                Role.CUSTOMER);

                assertNotNull(response);
                assertEquals("Login issue", response.title());
        }

        @Test
        void agentCanAccessTheirAssignedTicket() {
                User customer = new User(
                                "Customer",
                                "customer@example.com",
                                "password",
                                Role.CUSTOMER);

                ReflectionTestUtils.setField(customer, "id", 1L);

                User agent = new User(
                                "Agent",
                                "agent@example.com",
                                "password",
                                Role.AGENT);

                ReflectionTestUtils.setField(agent, "id", 3L);

                Ticket ticket = new Ticket(
                                "Login issue",
                                "Cannot log in",
                                "OPEN");

                ticket.setCreatedBy(customer);
                ticket.setAssignedTo(agent);

                when(ticketRepository.findById(100L))
                                .thenReturn(Optional.of(ticket));

                TicketResponse response = ticketService.getTicketById(
                                100L,
                                3L,
                                Role.AGENT);

                assertNotNull(response);
                assertEquals("Login issue", response.title());
        }

        @Test
        void agentCannotUpdateUnassignedTicket() {
                User customer = new User(
                                "Customer",
                                "customer@example.com",
                                "password",
                                Role.CUSTOMER);

                ReflectionTestUtils.setField(customer, "id", 1L);

                Ticket ticket = new Ticket(
                                "Login issue",
                                "Cannot log in",
                                "OPEN");

                ticket.setCreatedBy(customer);

                when(ticketRepository.findById(100L))
                                .thenReturn(Optional.of(ticket));

                UpdateTicketRequest request = new UpdateTicketRequest(
                                "Updated login issue",
                                "Updated description",
                                "OPEN",
                                TicketCategory.TECHNICAL_ISSUE,
                                TicketPriority.HIGH);

                assertThrows(
                                ForbiddenException.class,
                                () -> ticketService.updateTicket(
                                                100L,
                                                request,
                                                3L,
                                                Role.AGENT));
        }

        @Test
        void customerCannotDeleteTicket() {
                User customer = new User(
                                "Customer",
                                "customer@example.com",
                                "password",
                                Role.CUSTOMER);

                ReflectionTestUtils.setField(customer, "id", 1L);

                Ticket ticket = new Ticket(
                                "Login issue",
                                "Cannot log in",
                                "OPEN");

                ticket.setCreatedBy(customer);

                when(ticketRepository.findById(100L))
                                .thenReturn(Optional.of(ticket));

                assertThrows(
                                ForbiddenException.class,
                                () -> ticketService.deleteTicket(
                                                100L,
                                                1L,
                                                Role.CUSTOMER));
        }

        
@Test
void adminCanDeleteTicket() {
    User customer = new User(
            "Customer",
            "customer@example.com",
            "password",
            Role.CUSTOMER
    );

    ReflectionTestUtils.setField(customer, "id", 1L);

    Ticket ticket = new Ticket(
            "Login issue",
            "Cannot log in",
            "OPEN"
    );

    ticket.setCreatedBy(customer);

    when(ticketRepository.findById(100L))
            .thenReturn(Optional.of(ticket));

    ticketService.deleteTicket(
            100L,
            99L,
            Role.ADMIN
    );
}


    // An administrator should be able to assign a ticket to a valid agent.
    @Test
    void adminCanAssignTicketToAgent() {
        // Arrange: create a customer who originally submitted the ticket.
        User customer = new User(
                "Customer",
                "customer@example.com",
                "password",
                Role.CUSTOMER
        );
        ReflectionTestUtils.setField(customer, "id", 1L);

        // Arrange: create the agent who will receive the ticket.
        User agent = new User(
                "Agent",
                "agent@example.com",
                "password",
                Role.AGENT
        );
        ReflectionTestUtils.setField(agent, "id", 3L);

        // Arrange: create a ticket that belongs to the customer.
        Ticket ticket = new Ticket(
                "Login issue",
                "Cannot log in",
                "OPEN"
        );
        ticket.setCreatedBy(customer);

        // Mock the database lookups so no real database is needed.
        when(ticketRepository.findById(100L))
                .thenReturn(Optional.of(ticket));
        when(userRepository.findById(3L))
                .thenReturn(Optional.of(agent));
        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        // Act: an administrator assigns the ticket to agent ID 3.
        TicketResponse response = ticketService.assignTicket(
                100L,
                3L,
                99L,
                Role.ADMIN
        );

        // Assert: the ticket now points to the expected agent.
        assertSame(agent, ticket.getAssignedTo());
        assertNotNull(response);

        // Verify that the updated ticket was saved.
        verify(ticketRepository).save(ticket);
    }

    // Customers must not be allowed to assign tickets.
    @Test
    void customerCannotAssignTicket() {
        // Act and assert: assignment must fail before database access.
        assertThrows(
                ForbiddenException.class,
                () -> ticketService.assignTicket(
                        100L,
                        3L,
                        1L,
                        Role.CUSTOMER
                )
        );

        // The service checks the role before looking up the ticket.
        verify(ticketRepository, never()).findById(100L);
        verify(userRepository, never()).findById(3L);
        verify(ticketRepository, never()).save(
                org.mockito.ArgumentMatchers.any(Ticket.class)
        );
    }

    // An administrator must not assign a ticket to a customer.
    @Test
    void adminCannotAssignTicketToCustomer() {
        User customer = new User(
                "Customer",
                "customer@example.com",
                "password",
                Role.CUSTOMER
        );
        ReflectionTestUtils.setField(customer, "id", 2L);

        Ticket ticket = new Ticket(
                "Login issue",
                "Cannot log in",
                "OPEN"
        );

        when(ticketRepository.findById(100L))
                .thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(customer));

        // A customer exists, but their role makes them an invalid assignee.
        assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.assignTicket(
                        100L,
                        2L,
                        99L,
                        Role.ADMIN
                )
        );

        // An invalid assignment must never be saved.
        verify(ticketRepository, never()).save(
                org.mockito.ArgumentMatchers.any(Ticket.class)
        );
    }



}
