package com.relayguard.delivery;

public interface DeliveryTransport {
    DeliveryResult send(ClaimedDelivery delivery);
}
