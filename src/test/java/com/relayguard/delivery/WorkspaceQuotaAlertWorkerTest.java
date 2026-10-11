package com.relayguard.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.relayguard.identity.Workspace;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class WorkspaceQuotaAlertWorkerTest {
    @Test
    void sendsMinimalUsageMetadataToWorkspaceOwner() {
        JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        SmtpWorkspaceQuotaEmailSender sender = new SmtpWorkspaceQuotaEmailSender(
                mailSender, "alerts@relayguard.test", 100_000, 80);
        UUID workspaceId = UUID.randomUUID();
        WorkspaceQuotaAlertMessage alert = new WorkspaceQuotaAlertMessage(UUID.randomUUID(),
                "owner@example.test", workspaceId, "billing", LocalDate.of(2026, 10, 1), 80_000, 1);

        sender.send(alert);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        org.mockito.Mockito.verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertTrue(message.getTo()[0].equals("owner@example.test"));
        assertTrue(message.getText().contains("80000 of 100000"));
        assertTrue(message.getText().contains(workspaceId.toString()));
        assertFalse(message.getText().contains("sample-secret-payload"));
        assertFalse(message.getText().contains("project-api-key"));
    }

    @Test
    void marksAlertSentAfterSuccessfulDelivery() {
        WorkspaceQuotaAlertOutboxService outbox = org.mockito.Mockito.mock(WorkspaceQuotaAlertOutboxService.class);
        SmtpWorkspaceQuotaEmailSender sender = org.mockito.Mockito.mock(SmtpWorkspaceQuotaEmailSender.class);
        WorkspaceQuotaAlertMessage message = message(1);
        when(outbox.claimNext()).thenReturn(Optional.of(message));
        new WorkspaceQuotaAlertWorker(outbox, sender).processNext();
        verify(sender).send(message);
        verify(outbox).markSent(message);
    }

    @Test
    void schedulesRetryWhenMailDeliveryFails() {
        WorkspaceQuotaAlertOutboxService outbox = org.mockito.Mockito.mock(WorkspaceQuotaAlertOutboxService.class);
        SmtpWorkspaceQuotaEmailSender sender = org.mockito.Mockito.mock(SmtpWorkspaceQuotaEmailSender.class);
        WorkspaceQuotaAlertMessage message = message(2);
        when(outbox.claimNext()).thenReturn(Optional.of(message));
        doThrow(new MailSendException("smtp unavailable")).when(sender).send(message);
        new WorkspaceQuotaAlertWorker(outbox, sender).processNext();
        verify(outbox).retryOrFail(message);
    }

    @Test
    void reclaimsAnAbandonedLeaseAndBoundsRetryCount() {
        WorkspaceQuotaAlert alert = new WorkspaceQuotaAlert(new Workspace("test"),
                LocalDate.of(2026, 10, 1), "owner@example.test", 80_000);
        Instant now = Instant.now();
        assertTrue(alert.claim(now));
        assertFalse(alert.claim(now.plusSeconds(30)));
        assertTrue(alert.claim(now.plusSeconds(61)));
    }

    private WorkspaceQuotaAlertMessage message(int attempt) {
        return new WorkspaceQuotaAlertMessage(UUID.randomUUID(), "owner@example.test", UUID.randomUUID(),
                "test workspace", LocalDate.of(2026, 10, 1), 80_000, attempt);
    }
}
