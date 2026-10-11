package com.relayguard.event;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class WorkspaceQuotaExceededException extends ResponseStatusException {
    private final Instant resetsAt;

    public WorkspaceQuotaExceededException(Instant resetsAt) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Workspace monthly event quota reached");
        this.resetsAt = resetsAt;
    }

    public Instant getResetsAt() { return resetsAt; }
}
