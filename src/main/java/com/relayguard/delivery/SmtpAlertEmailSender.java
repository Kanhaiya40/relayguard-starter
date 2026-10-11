package com.relayguard.delivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpAlertEmailSender implements AlertEmailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpAlertEmailSender(JavaMailSender mailSender,
            @Value("${relayguard.notifications.email.from:no-reply@relayguard.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(EmailNotificationMessage notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.recipientEmail());
        message.setSubject("RelayGuard delivery failure threshold reached");
        message.setText("Project: " + notification.projectName() + " (" + notification.projectId() + ")\n"
                + "Event ID: " + notification.eventId() + "\n"
                + "Destination host: " + notification.destinationHost() + "\n"
                + "Recent delivery failures: " + notification.failureCount() + "\n"
                + "Latest failure code: " + (notification.failureCode() == null ? "DELIVERY_FAILED" : notification.failureCode())
                + "\n\nThe message excludes webhook payloads and credentials.");
        mailSender.send(message);
    }
}
