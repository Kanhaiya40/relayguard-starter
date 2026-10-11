package com.relayguard.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpAlertEmailSenderTest {
    @Test
    void sendsOnlySanitizedFailureMetadataAndNeverIncludesPayloads() {
        JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        SmtpAlertEmailSender sender = new SmtpAlertEmailSender(mailSender, "alerts@relayguard.test");
        UUID eventId = UUID.randomUUID();

        sender.send(new EmailNotificationMessage(UUID.randomUUID(), "ops@example.test", UUID.randomUUID(),
                "billing", eventId, "receiver.example.test", 3, "HTTP_500", 1));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertTrue(message.getTo()[0].equals("ops@example.test"));
        assertTrue(message.getText().contains(eventId.toString()));
        assertTrue(message.getText().contains("receiver.example.test"));
        assertFalse(message.getText().contains("sample-secret-payload"));
        assertFalse(message.getText().contains("project-api-key"));
    }
}
