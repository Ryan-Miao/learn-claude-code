package com.demo.learn.web;

import com.anthropic.backends.AnthropicBackend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientImpl;
import com.anthropic.client.okhttp.OkHttpClient;
import com.anthropic.core.ClientOptions;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Overrides Spring AI's AnthropicChatModel auto-configuration to inject a
 * traffic-capturing HttpClient wrapper around the Anthropic SDK's internal
 * OkHttp transport. This lets us capture the real HTTP request/response data
 * for every API round-trip, including those the SDK performs internally
 * during tool-call loops.
 */
@Configuration
@ConditionalOnProperty(name = "ai.provider", havingValue = "anthropic")
public class AnthropicCaptureConfig {

    @Bean("anthropicChatModel")
    AnthropicChatModel anthropicChatModel(
            AnthropicConnectionProperties connectionProperties,
            AnthropicChatProperties chatProperties,
            HttpTrafficCapture capture,
            ObjectProvider<ObservationRegistry> observationRegistry) {

        String apiKey = connectionProperties.getApiKey();
        String baseUrl = connectionProperties.getBaseUrl();

        // 1. Build the Anthropic backend (handles base URL + API key auth)
        AnthropicBackend.Builder backendBuilder = AnthropicBackend.builder();
        if (baseUrl != null && !baseUrl.isBlank()) {
            backendBuilder.baseUrl(baseUrl);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            backendBuilder.apiKey(apiKey);
        }
        AnthropicBackend backend = backendBuilder.build();

        // 2. Build the SDK's internal OkHttpClient (real HTTP transport)
        OkHttpClient sdkHttpClient = OkHttpClient.builder()
                .backend(backend)
                .build();

        // 3. Wrap with traffic-capturing decorator
        TrafficCapturingHttpClient capturingClient =
                new TrafficCapturingHttpClient(sdkHttpClient, capture);

        // 4. Build ClientOptions with our wrapped HttpClient.
        //    ClientOptions.build() will also wrap it in RetryingHttpClient.
        ClientOptions clientOptions = ClientOptions.builder()
                .httpClient(capturingClient)
                .maxRetries(2)
                .build();

        // 5. Create AnthropicClient directly (bypass AnthropicOkHttpClient.Builder
        //    which would overwrite our custom HttpClient)
        AnthropicClient anthropicClient = new AnthropicClientImpl(clientOptions);

        // 6. Build ChatModel with our custom client
        AnthropicChatOptions options = chatProperties.getOptions();
        return AnthropicChatModel.builder()
                .anthropicClient(anthropicClient)
                .options(options)
                .observationRegistry(
                        observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP))
                .build();
    }
}
