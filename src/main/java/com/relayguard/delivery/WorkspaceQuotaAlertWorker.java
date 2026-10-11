package com.relayguard.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "relayguard.notifications.email", name = "worker-enabled", havingValue = "true")
public class WorkspaceQuotaAlertWorker {
    private final WorkspaceQuotaAlertOutboxService outbox;
    private final SmtpWorkspaceQuotaEmailSender sender;

    public WorkspaceQuotaAlertWorker(WorkspaceQuotaAlertOutboxService outbox, SmtpWorkspaceQuotaEmailSender sender) {
        this.outbox = outbox;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${relayguard.notifications.email.poll-interval-ms:1000}")
    public void processNext() {
        outbox.claimNext().ifPresent(notification -> {
            try {
                sender.send(notification);
                outbox.markSent(notification);
            } catch (RuntimeException failure) {
                outbox.retryOrFail(notification);
            }
        });
    }
}
