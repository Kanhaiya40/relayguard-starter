package com.relayguard.event;

import com.relayguard.delivery.WorkspaceQuotaAlert;
import com.relayguard.delivery.WorkspaceQuotaAlertRepository;
import com.relayguard.identity.Workspace;
import com.relayguard.identity.WorkspaceMembership;
import com.relayguard.identity.WorkspaceMembershipRepository;
import com.relayguard.identity.WorkspaceRepository;
import com.relayguard.identity.WorkspaceRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceQuotaService {
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceMonthlyUsageRepository usage;
    private final WorkspaceQuotaAlertRepository alerts;
    private final int monthlyEventLimit;
    private final int warningPercent;
    private final int maxPayloadBytes;
    private final int maxDestinationsPerProject;
    private final int retentionDays;

    public WorkspaceQuotaService(WorkspaceRepository workspaces, WorkspaceMembershipRepository memberships,
            WorkspaceMonthlyUsageRepository usage, WorkspaceQuotaAlertRepository alerts,
            @Value("${relayguard.quotas.events-per-workspace-month:100000}") int monthlyEventLimit,
            @Value("${relayguard.quotas.warning-percent:80}") int warningPercent,
            @Value("${relayguard.ingest.max-request-bytes:1048576}") int maxPayloadBytes,
            @Value("${relayguard.quotas.destinations-per-project:10}") int maxDestinationsPerProject,
            @Value("${relayguard.retention.days:30}") int retentionDays) {
        if (monthlyEventLimit < 1 || warningPercent < 1 || warningPercent > 100
                || maxPayloadBytes < 1 || maxDestinationsPerProject < 1 || retentionDays < 1) {
            throw new IllegalArgumentException("Workspace quota settings must be positive and warning percent must not exceed 100");
        }
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.usage = usage;
        this.alerts = alerts;
        this.monthlyEventLimit = monthlyEventLimit;
        this.warningPercent = warningPercent;
        this.maxPayloadBytes = maxPayloadBytes;
        this.maxDestinationsPerProject = maxDestinationsPerProject;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public void recordAcceptedEvent(UUID workspaceId) {
        Workspace workspace = workspaces.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Project workspace is unavailable"));
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        LocalDate periodStart = month.atDay(1);
        WorkspaceMonthlyUsage current = usage.findByWorkspace_IdAndPeriodStart(workspaceId, periodStart)
                .orElseGet(() -> new WorkspaceMonthlyUsage(workspace, periodStart));
        if (current.getAcceptedEvents() >= monthlyEventLimit) {
            throw new WorkspaceQuotaExceededException(month.plusMonths(1).atDay(1)
                    .atStartOfDay(ZoneOffset.UTC).toInstant());
        }

        int accepted = current.increment();
        usage.saveAndFlush(current);
        int warningAt = (int) Math.ceil(monthlyEventLimit * (warningPercent / 100.0));
        if (accepted >= warningAt && !alerts.existsByWorkspace_IdAndPeriodStart(workspaceId, periodStart)) {
            memberships.findByWorkspace_IdAndRole(workspaceId, WorkspaceRole.OWNER).stream().findFirst()
                    .map(WorkspaceMembership::getUser).ifPresent(owner -> alerts.saveAndFlush(
                            new WorkspaceQuotaAlert(workspace, periodStart, owner.getEmail(), accepted)));
        }
    }

    @Transactional(readOnly = true)
    public WorkspaceUsageResponse getUsage(UUID userId, UUID workspaceId) {
        if (memberships.findByWorkspace_IdAndUser_Id(workspaceId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        LocalDate periodStart = month.atDay(1);
        int accepted = usage.findByWorkspace_IdAndPeriodStart(workspaceId, periodStart)
                .map(WorkspaceMonthlyUsage::getAcceptedEvents).orElse(0);
        return new WorkspaceUsageResponse(workspace.getId(), periodStart,
                month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                accepted, monthlyEventLimit, Math.max(0, monthlyEventLimit - accepted),
                (int) Math.ceil(monthlyEventLimit * (warningPercent / 100.0)),
                maxPayloadBytes, maxDestinationsPerProject, retentionDays);
    }
}
