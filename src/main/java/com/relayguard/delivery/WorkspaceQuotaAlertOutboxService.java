package com.relayguard.delivery;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceQuotaAlertOutboxService {
    private final WorkspaceQuotaAlertRepository alerts;

    public WorkspaceQuotaAlertOutboxService(WorkspaceQuotaAlertRepository alerts) { this.alerts = alerts; }

    @Transactional
    public Optional<WorkspaceQuotaAlertMessage> claimNext() {
        Instant now = Instant.now();
        return alerts.findPendingForUpdate(WorkspaceQuotaAlertStatus.PENDING,
                        WorkspaceQuotaAlertStatus.IN_PROGRESS, now, PageRequest.of(0, 1))
                .stream().findFirst().filter(alert -> alert.claim(now))
                .map(alert -> new WorkspaceQuotaAlertMessage(alert.getId(), alert.getRecipientEmail(),
                        alert.getWorkspaceId(), alert.getWorkspaceName(), alert.getPeriodStart(),
                        alert.getUsageCount(), alert.getAttemptCount()));
    }

    @Transactional
    public void markSent(WorkspaceQuotaAlertMessage message) {
        alerts.findById(message.id()).orElseThrow().sent(Instant.now());
    }

    @Transactional
    public void retryOrFail(WorkspaceQuotaAlertMessage message) {
        alerts.findById(message.id()).orElseThrow().retryOrFail(Instant.now());
    }
}
