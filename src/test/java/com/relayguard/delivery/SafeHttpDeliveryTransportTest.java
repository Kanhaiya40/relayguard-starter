package com.relayguard.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SafeHttpDeliveryTransportTest {
    @Test
    void classifiesHttpResponsesAndPreservesStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int status = switch (exchange.getRequestURI().getPath()) {
                case "/accepted" -> 202;
                case "/client-error" -> 429;
                case "/server-error" -> 503;
                default -> 204;
            };
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        PublicAddressResolver resolver = mock(PublicAddressResolver.class);
        when(resolver.resolve("127.0.0.1")).thenReturn(new InetAddress[] {InetAddress.getLoopbackAddress()});
        SafeHttpDeliveryTransport transport = new SafeHttpDeliveryTransport(new ObjectMapper(), resolver);
        try {
            assertEquals(202, transport.send(delivery(server, "/accepted")).httpStatus());
            assertEquals(429, transport.send(delivery(server, "/client-error")).httpStatus());
            assertEquals(503, transport.send(delivery(server, "/server-error")).httpStatus());
        } finally {
            transport.close();
            server.stop(0);
        }
    }

    @Test
    void recordsTimeoutAndDnsFailureWithoutLeakingExceptionText() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(6500); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            try { exchange.sendResponseHeaders(204, -1); } catch (java.io.IOException ignored) { }
            exchange.close();
        });
        server.start();
        PublicAddressResolver resolver = mock(PublicAddressResolver.class);
        when(resolver.resolve("127.0.0.1")).thenReturn(new InetAddress[] {InetAddress.getLoopbackAddress()});
        when(resolver.resolve("does-not-resolve.invalid")).thenThrow(new UnknownHostException("private diagnostic"));
        SafeHttpDeliveryTransport transport = new SafeHttpDeliveryTransport(new ObjectMapper(), resolver);
        try {
            DeliveryResult timeout = transport.send(delivery(server, "/slow"));
            assertEquals("TIMEOUT", timeout.errorCode());
            DeliveryResult dns = transport.send(new ClaimedDelivery(UUID.randomUUID(), UUID.randomUUID(), 1,
                    UUID.randomUUID(), "test", "{}", Instant.now(), "https://does-not-resolve.invalid/hook"));
            assertEquals("UNSAFE_OR_UNRESOLVED_HOST", dns.errorCode());
            assertNull(dns.httpStatus());
        } finally {
            transport.close();
            server.stop(0);
        }
    }

    private ClaimedDelivery delivery(HttpServer server, String path) {
        return new ClaimedDelivery(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(),
                "test.event", "{\"safe\":true}", Instant.now(),
                "http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
