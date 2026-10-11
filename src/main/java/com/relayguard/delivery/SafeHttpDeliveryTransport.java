package com.relayguard.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Arrays;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

@Component
public class SafeHttpDeliveryTransport implements DeliveryTransport {
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient client;

    public SafeHttpDeliveryTransport(ObjectMapper objectMapper, PublicAddressResolver resolver) {
        this.objectMapper = objectMapper;
        DnsResolver validatedDns = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return resolver.resolve(host);
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        PoolingHttpClientConnectionManager manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(validatedDns)
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                .setConnectTimeout(Timeout.ofSeconds(3))
                .setResponseTimeout(Timeout.ofSeconds(5))
                .build();
        this.client = HttpClients.custom()
                .setConnectionManager(manager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .build();
    }

    @Override
    public DeliveryResult send(ClaimedDelivery delivery) {
        long started = System.nanoTime();
        try {
            HttpPost request = new HttpPost(delivery.destinationUrl());
            request.setHeader("User-Agent", "RelayGuard/0.1");
            request.setHeader("X-RelayGuard-Event-Id", delivery.eventId().toString());
            request.setHeader("X-RelayGuard-Event-Type", delivery.eventType());
            request.setEntity(new StringEntity(envelope(delivery), ContentType.APPLICATION_JSON));
            Integer status = client.execute(request, response -> response.getCode());
            return new DeliveryResult(status, status >= 200 && status < 300 ? null : "HTTP_" + status,
                    elapsedMillis(started));
        } catch (Exception exception) {
            String code = isCausedBy(exception, SocketTimeoutException.class)
                    ? "TIMEOUT"
                    : isCausedBy(exception, UnknownHostException.class) ? "UNSAFE_OR_UNRESOLVED_HOST"
                    : isCausedBy(exception, java.net.ConnectException.class) ? "CONNECTION_FAILED"
                    : "DELIVERY_FAILED";
            return new DeliveryResult(null, code, elapsedMillis(started));
        }
    }

    private String envelope(ClaimedDelivery delivery) throws IOException {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("id", delivery.eventId().toString());
        envelope.put("eventType", delivery.eventType());
        envelope.set("payload", objectMapper.readTree(delivery.payloadJson()));
        envelope.put("receivedAt", delivery.receivedAt().toString());
        return objectMapper.writeValueAsString(envelope);
    }

    private boolean isCausedBy(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    @PreDestroy
    public void close() throws IOException {
        client.close();
    }
}
