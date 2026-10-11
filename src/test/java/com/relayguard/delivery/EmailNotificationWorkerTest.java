package com.relayguard.delivery;

import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

class EmailNotificationWorkerTest {
    private final EmailNotificationOutboxService outbox = org.mockito.Mockito.mock(EmailNotificationOutboxService.class);
    private final AlertEmailSender sender = org.mockito.Mockito.mock(AlertEmailSender.class);

    @Test
    void recordsSuccessfulSendWithoutTouchingDeliveryQueue() {
        EmailNotificationMessage notification = notification(1);
        when(outbox.claimNext()).thenReturn(Optional.of(notification));
        EmailNotificationWorker worker = new EmailNotificationWorker(outbox, sender);

        worker.processNext();

        verify(sender).send(notification);
        verify(outbox).markSent(eq(notification.id()), any(java.time.Instant.class));
    }

    @Test
    void schedulesBoundedRetryWhenEmailProviderFails() {
        EmailNotificationMessage notification = notification(2);
        when(outbox.claimNext()).thenReturn(Optional.of(notification));
        doThrow(new MailSendException("smtp unavailable")).when(sender).send(notification);
        EmailNotificationWorker worker = new EmailNotificationWorker(outbox, sender);

        worker.processNext();

        verify(outbox).retryOrFail(eq(notification.id()), eq(2), any(java.time.Instant.class));
    }

    private EmailNotificationMessage notification(int attempt) {
        return new EmailNotificationMessage(UUID.randomUUID(), "alerts@example.test", UUID.randomUUID(),
                "test project", UUID.randomUUID(), "receiver.example.test", 3, "HTTP_ERROR", attempt);
    }
}
