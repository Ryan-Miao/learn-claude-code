package com.demo.learn.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides a RestClient.Builder that captures HTTP traffic via HttpTrafficInterceptor.
 * <p>
 * The interceptor is request-scoped: each call to {@link #createTrafficList()} returns
 * a fresh list that the interceptor writes into. ChatController reads from this list
 * after the chat call completes.
 */
@Configuration
public class RestClientConfig {

    private static final ThreadLocal<List<HttpTrafficInterceptor.HttpTraffic>> ACTIVE_TRAFFIC =
            new ThreadLocal<>();

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestInterceptor(new TrafficInterceptorBridge());
    }

    /**
     * Create a new traffic list and bind it to the current thread.
     * Returns the list for ChatController to read after the call.
     */
    public static List<HttpTrafficInterceptor.HttpTraffic> createTrafficList() {
        List<HttpTrafficInterceptor.HttpTraffic> list = new ArrayList<>();
        ACTIVE_TRAFFIC.set(list);
        return list;
    }

    /**
     * Clear the thread-local traffic list. Call after the chat request completes.
     */
    public static void clearTrafficList() {
        ACTIVE_TRAFFIC.remove();
    }

    /**
     * Bridge interceptor that delegates to a real HttpTrafficInterceptor
     * bound to the current thread's traffic list.
     */
    private static class TrafficInterceptorBridge implements ClientHttpRequestInterceptor {
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(org.springframework.http.HttpRequest request, byte[] body,
                                            org.springframework.http.client.ClientHttpRequestExecution execution)
                throws java.io.IOException {
            List<HttpTrafficInterceptor.HttpTraffic> trafficList = ACTIVE_TRAFFIC.get();
            if (trafficList != null) {
                return new HttpTrafficInterceptor(trafficList).intercept(request, body, execution);
            }
            return execution.execute(request, body);
        }
    }
}
