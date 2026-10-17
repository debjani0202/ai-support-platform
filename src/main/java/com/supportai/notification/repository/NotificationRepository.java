package com.supportai.notification.repository;

import com.supportai.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(
            Long recipientId,
            Pageable pageable
    );

    Page<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(
            Long recipientId,
            Pageable pageable
    );

    long countByRecipientIdAndReadFalse(Long recipientId);
}
