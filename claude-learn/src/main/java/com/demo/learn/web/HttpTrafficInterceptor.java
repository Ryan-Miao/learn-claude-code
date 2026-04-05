package com.demo.learn.web;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Captures real HTTP request/response data for each LLM API call.
 * Per-request scoped: a fresh traffic list is injected via constructor.
 */
public class HttpTrafficInterceptor implements ClientHttpRequestInterceptor {

    private final List<HttpTraffic> trafficList;
    private final int[] roundCounter = {0};

    public HttpTrafficInterceptor(List<HttpTraffic> trafficList) {
        this.trafficList = trafficList;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        int round = ++roundCounter[0];
        long start = System.currentTimeMillis();

        // Capture request data
        String url = request.getURI().toString();
        String method = request.getMethod().name();
        List<CapturedHeader> reqHeaders = captureHeaders(request.getHeaders());
        String reqBody = new String(body, StandardCharsets.UTF_8);

        // Execute and capture response
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - start;
            trafficList.add(new HttpTraffic(
                    round, url, method, -1, duration,
                    reqHeaders, truncate(reqBody, 50000),
                    List.of(), e.getMessage() != null ? e.getMessage() : "Connection error"
            ));
            throw e;
        }

        // Buffer response body so we can read it without consuming the stream
        byte[] responseBodyBytes = StreamUtils.copyToByteArray(response.getBody());
        long duration = System.currentTimeMillis() - start;

        List<CapturedHeader> respHeaders = captureHeaders(response.getHeaders());
        String respBody = new String(responseBodyBytes, StandardCharsets.UTF_8);

        trafficList.add(new HttpTraffic(
                round, url, method,
                response.getStatusCode().value(), duration,
                reqHeaders, truncate(reqBody, 50000),
                respHeaders, truncate(respBody, 50000)
        ));

        // Return a response with the buffered body so the caller can still read it
        return new BufferedClientHttpResponse(response, responseBodyBytes);
    }

    private List<CapturedHeader> captureHeaders(org.springframework.http.HttpHeaders headers) {
        List<CapturedHeader> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                boolean sensitive = isSensitiveHeader(entry.getKey(), value);
                String displayValue = sensitive ? maskValue(value) : value;
                result.add(new CapturedHeader(entry.getKey(), displayValue, sensitive));
            }
        }
        return result;
    }

    static boolean isSensitiveHeader(String name, String value) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("x-api-key") || lower.equals("authorization")
                || lower.equals("token") || lower.startsWith("key-")) {
            return true;
        }
        if (value != null && (value.startsWith("sk-") || value.startsWith("Bearer "))) {
            return true;
        }
        return false;
    }

    static String maskValue(String value) {
        if (value == null) return "***REDACTED***";
        value = value.replaceAll("sk-[a-zA-Z0-9]{10,}", "***REDACTED***");
        value = value.replaceAll("Bearer\\s+\\S+", "Bearer ***REDACTED***");
        return value;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "... (truncated)" : text;
    }

    // --- Data records ---

    public record CapturedHeader(String name, String value, boolean sensitive) {}
    public record HttpTraffic(
            int round,
            String url,
            String method,
            int statusCode,
            long durationMs,
            List<CapturedHeader> requestHeaders,
            String requestBody,
            List<CapturedHeader> responseHeaders,
            String responseBody
    ) {}

    /**
     * Wraps a ClientHttpResponse with a buffered body so the stream can be read multiple times.
     */
    private static class BufferedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse original;
        private final byte[] body;

        BufferedClientHttpResponse(ClientHttpResponse original, byte[] body) {
            this.original = original;
            this.body = body;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return original.getHeaders();
        }

        @Override
        public org.springframework.http.HttpStatus getStatusCode() throws IOException {
            return original.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return original.getStatusText();
        }

        @Override
        public void close() {
            original.close();
        }
    }
}
