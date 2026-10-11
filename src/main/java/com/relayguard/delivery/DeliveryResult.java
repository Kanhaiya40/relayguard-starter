package com.relayguard.delivery;

public record DeliveryResult(Integer httpStatus, String errorCode, long durationMs) {
    public boolean succeeded() {
        return httpStatus != null && httpStatus >= 200 && httpStatus < 300;
    }
}
