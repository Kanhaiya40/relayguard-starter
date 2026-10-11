package com.relayguard.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class RequestBodySizeFilter extends OncePerRequestFilter {
    private final int maxBytes;
    private final ObjectMapper objectMapper;

    public RequestBodySizeFilter(
            @Value("${relayguard.ingest.max-request-bytes:1048576}") int maxBytes,
            ObjectMapper objectMapper) {
        if (maxBytes < 1 || maxBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Maximum request size must be between 1 and 2147483646 bytes");
        }
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST") || !request.getRequestURI().equals("/api/v1/events");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            tooLarge(response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            tooLarge(response);
            return;
        }
        chain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    private void tooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of("PAYLOAD_TOO_LARGE", "Request body exceeds the configured " + maxBytes + "-byte limit", 413));
    }

    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = Arrays.copyOf(body, body.length);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new IllegalStateException("Asynchronous request reading is not supported");
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), getCharacterEncodingOrUtf8()));
        }

        private java.nio.charset.Charset getCharacterEncodingOrUtf8() {
            String encoding = getCharacterEncoding();
            return encoding == null ? java.nio.charset.StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding);
        }
    }
}
