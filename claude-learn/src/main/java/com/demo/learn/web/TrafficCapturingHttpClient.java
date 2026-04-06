package com.demo.learn.web;

import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpRequestBody;
import com.anthropic.core.http.HttpResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Decorator around Anthropic SDK's HttpClient that captures real HTTP request/response data.
 * Each execute() call is wrapped with timing, body buffering, and header capture.
 * <p>
 * The request body (HttpRequestBody) is buffered via writeTo() and replaced with a
 * repeatable byte-array body so the delegate can still read it.
 * The response body (InputStream) is fully consumed, buffered, and replayed via
 * a new ByteArrayInputStream.
 */
public class TrafficCapturingHttpClient implements HttpClient {

    private static final int MAX_BODY_CAPTURE = 102400; // 100KB

    private final HttpClient delegate;
    private final HttpTrafficCapture capture;

    public TrafficCapturingHttpClient(HttpClient delegate, HttpTrafficCapture capture) {
        this.delegate = delegate;
        this.capture = capture;
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions options) {
        long start = System.currentTimeMillis();
        int round = capture.nextRound();

        // --- Capture request ---
        String url = request.url();
        String method = request.method().name();
        Map<String, String> reqHeaders = extractHeaders(request.headers());

        // Buffer request body and make it repeatable
        String requestBodyStr = "";
        HttpRequest repeatableRequest = request;
        HttpRequestBody originalBody = request.body();
        if (originalBody != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            originalBody.writeTo(baos);
            byte[] bodyBytes = baos.toByteArray();
            requestBodyStr = truncateBody(new String(bodyBytes, StandardCharsets.UTF_8));
            repeatableRequest = request.toBuilder()
                    .body(new ByteArrayRequestBody(bodyBytes, originalBody.contentType()))
                    .build();
        }

        // --- Execute real HTTP call ---
        HttpResponse response = delegate.execute(repeatableRequest, options);
        long duration = System.currentTimeMillis() - start;

        // --- Buffer response ---
        int statusCode = response.statusCode();
        Headers respHeaders = response.headers();
        byte[] respBytes;
        try (InputStream bodyStream = response.body()) {
            respBytes = bodyStream.readAllBytes();
        } catch (IOException e) {
            respBytes = new byte[0];
        }
        String responseBodyStr = truncateBody(new String(respBytes, StandardCharsets.UTF_8));

        // --- Record captured round ---
        capture.capture(new HttpTrafficCapture.CapturedRound(
                round, url, method,
                maskSensitiveHeaders(reqHeaders), requestBodyStr,
                statusCode,
                extractHeaders(respHeaders),
                responseBodyStr,
                duration
        ));

        // Return buffered response (InputStream can be read by caller)
        return new BufferedHttpResponse(statusCode, respHeaders, respBytes);
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) {
        try {
            return CompletableFuture.completedFuture(execute(request, options));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void close() {
        delegate.close();
    }

    // ---- helpers ----

    private String truncateBody(String body) {
        if (body.length() > MAX_BODY_CAPTURE) {
            return body.substring(0, MAX_BODY_CAPTURE) + "\n... [truncated at 100KB]";
        }
        return body;
    }

    static Map<String, String> extractHeaders(Headers headers) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : headers.names()) {
            List<String> values = headers.values(name);
            if (!values.isEmpty()) {
                result.put(name, String.join(", ", values));
            }
        }
        return result;
    }

    private static Map<String, String> maskSensitiveHeaders(Map<String, String> headers) {
        Map<String, String> masked = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.contains("authorization") || key.contains("api-key")
                    || key.contains("x-api-key") || key.contains("apikey")) {
                masked.put(entry.getKey(), "***REDACTED***");
            } else {
                masked.put(entry.getKey(), entry.getValue());
            }
        }
        return masked;
    }

    // ---- inner classes ----

    /** Repeatable request body backed by a byte array. */
    static class ByteArrayRequestBody implements HttpRequestBody {
        private final byte[] bytes;
        private final String contentType;

        ByteArrayRequestBody(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }

        @Override
        public void writeTo(OutputStream outputStream) {
            try {
                outputStream.write(bytes);
            } catch (IOException ignored) { }
        }

        @Override
        public String contentType() { return contentType; }

        @Override
        public long contentLength() { return bytes.length; }

        @Override
        public boolean repeatable() { return true; }

        @Override
        public void close() { /* no-op */ }
    }

    /** HttpResponse backed by a byte array so the body InputStream can be read multiple times. */
    static class BufferedHttpResponse implements HttpResponse {
        private final int statusCode;
        private final Headers headers;
        private final byte[] bodyBytes;

        BufferedHttpResponse(int statusCode, Headers headers, byte[] bodyBytes) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.bodyBytes = bodyBytes;
        }

        @Override
        public int statusCode() { return statusCode; }

        @Override
        public Headers headers() { return headers; }

        @Override
        public InputStream body() { return new ByteArrayInputStream(bodyBytes); }

        @Override
        public void close() { /* no-op — bytes are in memory */ }
    }
}
