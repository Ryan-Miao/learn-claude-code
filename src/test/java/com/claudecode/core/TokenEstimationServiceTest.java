package com.claudecode.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * TokenEstimationService 单元测试。
 */
class TokenEstimationServiceTest {

    private final TokenEstimationService service = new TokenEstimationService();

    // ==================== estimateTokens ====================

    @Test
    @DisplayName("null or empty text returns 0")
    void estimateTokens_nullOrEmpty() {
        assertThat(service.estimateTokens(null)).isEqualTo(0);
        assertThat(service.estimateTokens("")).isEqualTo(0);
    }

    @Test
    @DisplayName("short English text returns at least 1 token")
    void estimateTokens_shortText() {
        assertThat(service.estimateTokens("hi")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("English text roughly 4 chars per token")
    void estimateTokens_english() {
        String text = "The quick brown fox jumps over the lazy dog."; // 44 chars
        int tokens = service.estimateTokens(text);
        // Expect roughly 44/4 = 11 tokens, allow some variance
        assertThat(tokens).isBetween(8, 18);
    }

    @Test
    @DisplayName("CJK text has higher token density")
    void estimateTokens_cjk() {
        String text = "这是一段中文测试文本"; // 9 CJK chars
        int tokens = service.estimateTokens(text);
        // CJK: ~1.5 chars/token → ~6 tokens
        assertThat(tokens).isBetween(4, 10);
    }

    @Test
    @DisplayName("code text has code-specific ratio")
    void estimateTokens_code() {
        String text = "if (x == 0) { return; }"; // contains code chars
        int tokens = service.estimateTokens(text);
        assertThat(tokens).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("JSON text detected and uses JSON ratio")
    void estimateTokens_json() {
        String json = """
                {"name": "test", "value": 42, "nested": {"key": "val"}}""";
        int tokens = service.estimateTokens(json);
        assertThat(tokens).isGreaterThan(5);
    }

    // ==================== estimateCost ====================

    @Test
    @DisplayName("Sonnet pricing is default")
    void estimateCost_sonnet() {
        double cost = service.estimateCost(1_000_000, 1_000_000, "claude-sonnet-4");
        // Input: $3/M + Output: $15/M = $18
        assertThat(cost).isCloseTo(18.0, within(0.01));
    }

    @Test
    @DisplayName("Opus pricing is higher")
    void estimateCost_opus() {
        double cost = service.estimateCost(1_000_000, 1_000_000, "claude-opus-4");
        // Input: $15/M + Output: $75/M = $90
        assertThat(cost).isCloseTo(90.0, within(0.01));
    }

    @Test
    @DisplayName("Haiku pricing is cheaper")
    void estimateCost_haiku() {
        double cost = service.estimateCost(1_000_000, 1_000_000, "claude-haiku-3");
        // Input: $0.25/M + Output: $1.25/M = $1.50
        assertThat(cost).isCloseTo(1.50, within(0.01));
    }

    @Test
    @DisplayName("zero tokens cost zero")
    void estimateCost_zero() {
        assertThat(service.estimateCost(0, 0, "sonnet")).isEqualTo(0.0);
    }

    // ==================== formatTokenCount ====================

    @Test
    @DisplayName("format small counts as-is")
    void formatTokenCount_small() {
        assertThat(service.formatTokenCount(42)).isEqualTo("42");
        assertThat(service.formatTokenCount(999)).isEqualTo("999");
    }

    @Test
    @DisplayName("format thousands as K")
    void formatTokenCount_thousands() {
        assertThat(service.formatTokenCount(1000)).isEqualTo("1.0K");
        assertThat(service.formatTokenCount(5500)).isEqualTo("5.5K");
    }

    @Test
    @DisplayName("format millions as M")
    void formatTokenCount_millions() {
        assertThat(service.formatTokenCount(1_000_000)).isEqualTo("1.0M");
        assertThat(service.formatTokenCount(2_500_000)).isEqualTo("2.5M");
    }

    // ==================== estimateMessageTokens ====================

    @Test
    @DisplayName("message tokens include overhead")
    void estimateMessageTokens_overhead() {
        int contentTokens = service.estimateTokens("Hello world");
        int messageTokens = service.estimateMessageTokens("user", "Hello world");
        assertThat(messageTokens).isEqualTo(contentTokens + 4);
    }

    // ==================== estimateToolDefinitionTokens ====================

    @Test
    @DisplayName("tool definition includes structural overhead")
    void estimateToolDefinitionTokens_overhead() {
        int tokens = service.estimateToolDefinitionTokens("Read", "Read a file", "{\"type\":\"object\"}");
        assertThat(tokens).isGreaterThanOrEqualTo(20); // at least the 20 overhead
    }
}
