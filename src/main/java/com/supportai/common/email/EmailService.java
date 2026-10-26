package com.supportai.common.email;

public interface EmailService {

    /**
     * Sends a password reset email to the recipient with the recovery link.
     *
     * @param recipientEmail the target user's email address
     * @param resetLink the full password recovery link
     */
    void sendPasswordResetEmail(String recipientEmail, String resetLink);
}
