package com.supportai.common.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Local development and testing email service that outputs to application logs.
 * Production environments can substitute an SMTP or cloud provider implementation
 * implementing EmailService without changes to business logic.
 */
@Service
public class ConsoleEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String resetLink) {
        log.info("================================================================================");
        log.info("[LOCAL DEV - CONSOLE EMAIL SERVICE] Simulated password reset email dispatch");
        log.info("Recipient: {}", recipientEmail);
        log.info("Reset Link: {}", resetLink);
        log.info("Note: Actual emails are not sent. Use the link above for testing.");
        log.info("================================================================================");
    }
}
