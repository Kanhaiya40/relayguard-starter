package com.relayguard.delivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpWorkspaceQuotaEmailSender {
    private final JavaMailSender mailSender;
    private final String from;
    private final int monthlyEventLimit;
    private final int warningPercent;

    public SmtpWorkspaceQuotaEmailSender(JavaMailSender mailSender,
            @Value("${relayguard.notifications.email.from:no-reply@relayguard.local}") String from,
            @Value("${relayguard.quotas.events-per-workspace-month:100000}") int monthlyEventLimit,
            @Value("${relayguard.quotas.warning-percent:80}") int warningPercent) {
        this.mailSender = mailSender;
        this.from = from;
        this.monthlyEventLimit = monthlyEventLimit;
        this.warningPercent = warningPercent;
    }

    public void send(WorkspaceQuotaAlertMessage notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.recipientEmail());
        message.setSubject("RelayGuard workspace is nearing its monthly event limit");
        message.setText("Workspace: " + notification.workspaceName() + " (" + notification.workspaceId() + ")\n"
                + "Usage period starts: " + notification.periodStart() + " UTC\n"
                + "Accepted events: " + notification.usageCount() + " of " + monthlyEventLimit + " ("
                + warningPercent + "% warning threshold)\n\n"
                + "New event ingestion will be paused when the monthly limit is reached and resumes at the next UTC month.\n"
                + "This message does not include webhook payloads or credentials.");
        mailSender.send(message);
    }
}
