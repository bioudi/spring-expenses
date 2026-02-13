package com.expensetracker.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final Resend resend;

    @Value("${resend.from-address:}")
    private String fromAddress;

    public EmailService(@Value("${resend.api-key:}") String apiKey) {
        this.resend = apiKey.isBlank() ? null : new Resend(apiKey);
    }

    public void sendHtmlEmail(String[] recipients, String subject, String htmlBody) {
        if (resend == null) {
            log.warn("Resend API key not configured. Skipping email: '{}'", subject);
            return;
        }

        for (String recipient : recipients) {
            try {
                CreateEmailOptions params = CreateEmailOptions.builder()
                        .from(fromAddress)
                        .to(recipient)
                        .subject(subject)
                        .html(htmlBody)
                        .build();

                CreateEmailResponse response = resend.emails().send(params);
                log.info("Email sent to '{}'. Subject: '{}', ID: {}", recipient, subject, response.getId());
            } catch (ResendException e) {
                log.error("Failed to send email to '{}'. Subject: '{}', Error: {}", recipient, subject, e.getMessage(), e);
                throw new RuntimeException("Failed to send email to " + recipient, e);
            }
        }
    }
}
