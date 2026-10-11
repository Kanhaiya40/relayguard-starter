package com.relayguard.delivery;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DestinationQuotaExceededException extends ResponseStatusException {
    private final int destinationLimit;

    public DestinationQuotaExceededException(int destinationLimit) {
        super(HttpStatus.CONFLICT, "Project destination limit reached");
        this.destinationLimit = destinationLimit;
    }

    public int getDestinationLimit() { return destinationLimit; }
}
