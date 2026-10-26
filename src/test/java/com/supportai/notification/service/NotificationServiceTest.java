package com.supportai.notification.service;

import com.supportai.exception.ResourceNotFoundException;
import com.supportai.notification.dto.NotificationResponse;
import com.supportai.notification.entity.Notification;
import com.supportai.notification.repository.NotificationRepository;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.ticket.entity.Ticket;
import com.supportai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        notificationService = new NotificationService(notificationRepository);
    }

    @Test
    void getMyNotifications_withUnreadOnlyFalse_queriesAllAndMapsToPageResponse() {
        Long userId = 1L;
        Instant now = Instant.now();

        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(10L);

        Notification notification = mock(Notification.class);
        when(notification.getId()).thenReturn(100L);
        when(notification.getTicket()).thenReturn(ticket);
        when(notification.getType()).thenReturn("TICKET_ASSIGNED");
        when(notification.getMessage()).thenReturn("You have been assigned to Ticket #10");
        when(notification.isRead()).thenReturn(false);
        when(notification.getCreatedAt()).thenReturn(now);

        PageRequest pageRequest = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageImpl<Notification> page = new PageImpl<>(List.of(notification), pageRequest, 1);

        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<NotificationResponse> result = notificationService.getMyNotifications(userId, 0, 20, false);

        assertNotNull(result);
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1, result.totalElements());
        assertEquals(1, result.totalPages());
        assertTrue(result.first());
        assertTrue(result.last());
        assertEquals(1, result.content().size());

        NotificationResponse response = result.content().get(0);
        assertEquals(100L, response.id());
        assertEquals(10L, response.ticketId());
        assertEquals("TICKET_ASSIGNED", response.type());
        assertEquals("You have been assigned to Ticket #10", response.message());
        assertFalse(response.read());
        assertEquals(now, response.createdAt());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipientIdOrderByCreatedAtDesc(eq(userId), captor.capture());
        Pageable capturedPageable = captor.getValue();
        assertEquals(0, capturedPageable.getPageNumber());
        assertEquals(20, capturedPageable.getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), capturedPageable.getSort());
        verify(notificationRepository, never()).findByRecipientIdAndReadFalseOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getMyNotifications_withUnreadOnlyTrue_queriesUnreadOnlyAndMapsToPageResponse() {
        Long userId = 1L;

        Notification notification = mock(Notification.class);
        when(notification.getId()).thenReturn(101L);
        when(notification.getTicket()).thenReturn(null);
        when(notification.getType()).thenReturn("GENERAL");
        when(notification.getMessage()).thenReturn("System alert");
        when(notification.isRead()).thenReturn(false);
        when(notification.getCreatedAt()).thenReturn(Instant.now());

        PageRequest pageRequest = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageImpl<Notification> page = new PageImpl<>(List.of(notification), pageRequest, 25);

        when(notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<NotificationResponse> result = notificationService.getMyNotifications(userId, 1, 10, true);

        assertNotNull(result);
        assertEquals(1, result.page());
        assertEquals(10, result.size());
        assertEquals(25, result.totalElements());
        assertEquals(3, result.totalPages());
        assertFalse(result.first());
        assertFalse(result.last());
        assertEquals(1, result.content().size());
        assertNull(result.content().get(0).ticketId());

        verify(notificationRepository).findByRecipientIdAndReadFalseOrderByCreatedAtDesc(eq(userId), any(Pageable.class));
        verify(notificationRepository, never()).findByRecipientIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getMyNotifications_validatesPagination_negativePageThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.getMyNotifications(1L, -1, 20, false)
        );
        assertEquals("Page number cannot be negative", ex.getMessage());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void getMyNotifications_validatesPagination_sizeLessThanOneThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.getMyNotifications(1L, 0, 0, false)
        );
        assertEquals("Page size must be between 1 and 100", ex.getMessage());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void getMyNotifications_validatesPagination_negativeSizeThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.getMyNotifications(1L, 0, -1, false)
        );
        assertEquals("Page size must be between 1 and 100", ex.getMessage());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void getMyNotifications_validatesPagination_sizeGreaterThan100Throws() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.getMyNotifications(1L, 0, 101, false)
        );
        assertEquals("Page size must be between 1 and 100", ex.getMessage());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void getMyNotifications_emptyRepositoryResult_returnsEmptyPageResponse() {
        Long userId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageImpl<Notification> emptyPage = new PageImpl<>(List.of(), pageRequest, 0);

        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(emptyPage);

        PageResponse<NotificationResponse> result = notificationService.getMyNotifications(userId, 0, 20, false);

        assertNotNull(result);
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
        assertTrue(result.first());
        assertTrue(result.last());
        assertTrue(result.content().isEmpty());
    }

    @Test
    void getUnreadCount_returnsCountFromRepository() {
        when(notificationRepository.countByRecipientIdAndReadFalse(1L)).thenReturn(7L);

        long count = notificationService.getUnreadCount(1L);

        assertEquals(7L, count);
        verify(notificationRepository).countByRecipientIdAndReadFalse(1L);
    }

    @Test
    void markAsRead_success() {
        Long notificationId = 10L;
        Long currentUserId = 1L;

        User recipient = mock(User.class);
        when(recipient.getId()).thenReturn(currentUserId);

        Notification notification = mock(Notification.class);
        when(notification.getRecipient()).thenReturn(recipient);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(notificationId, currentUserId);

        verify(notification).markAsRead();
    }

    @Test
    void markAsRead_notFound_throwsResourceNotFoundException() {
        Long notificationId = 999L;
        Long currentUserId = 1L;

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.markAsRead(notificationId, currentUserId));
    }

    @Test
    void markAsRead_differentRecipient_throwsResourceNotFoundException() {
        Long notificationId = 10L;
        Long currentUserId = 1L;
        Long ownerId = 2L;

        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);

        Notification notification = mock(Notification.class);
        when(notification.getRecipient()).thenReturn(owner);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        assertThrows(ResourceNotFoundException.class, () -> notificationService.markAsRead(notificationId, currentUserId));
        verify(notification, never()).markAsRead();
    }

    @Test
    void createNotification_savesNotification() {
        User recipient = mock(User.class);
        Ticket ticket = mock(Ticket.class);

        notificationService.createNotification(recipient, ticket, "STATUS_CHANGE", "Status changed");

        verify(notificationRepository).save(any(Notification.class));
    }
}
