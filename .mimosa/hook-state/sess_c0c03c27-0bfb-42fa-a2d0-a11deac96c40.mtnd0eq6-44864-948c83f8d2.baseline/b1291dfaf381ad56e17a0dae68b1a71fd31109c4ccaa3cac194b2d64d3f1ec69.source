package com.nexus.campus.service;

import com.nexus.campus.dto.EmailRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * Send registration confirmation email (simulated).
     */
    public void sendRegistrationConfirm(String email, String username) {
        log.info("[EmailService] Registration confirmation sent to={}, username={}", email, username);
    }

    /**
     * Send reply notification email (simulated).
     */
    public void sendReplyNotification(String toEmail, String postTitle) {
        log.info("[EmailService] Reply notification sent to={}, postTitle={}", toEmail, postTitle);
    }

    /**
     * Send audit result email (simulated).
     */
    public void sendAuditResult(String toEmail, String postTitle, boolean approved) {
        log.info("[EmailService] Audit result sent to={}, postTitle={}, approved={}", toEmail, postTitle, approved);
    }
}
