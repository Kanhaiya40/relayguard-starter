package com.relayguard.delivery;

public interface AlertEmailSender {
    void send(EmailNotificationMessage notification);
}
