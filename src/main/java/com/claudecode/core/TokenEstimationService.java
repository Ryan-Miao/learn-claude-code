package com.claudecode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Token 估算服务 —— 对应 claude-code 中 tokenEstimation。
 * <p>
 * 使用简化的 cl100k_base / o200k_base 编码近似估算 token 数量。
 * 不使用真正的 BPE 编码器（避免大型词表依赖），而是基于统计规律近似。
 * <p>
 * 近似规则：
 * <ul>
 *   <li>英文文本：~4 chars/token</li>
 *   <li>代码：~3.5 chars/token</li>
 *   <li>中文/日文/韩文：~1.5 chars/token</li>
 *   <li>JSON/结构化数据：~3 chars/token</li>
 * </ul>
 */
public class TokenEstimationService {

    private static final Logger log = LoggerFactory.getLogger(TokenEstimationService.class);

    private static final Pattern CJK_PATTERN = Pattern.compile("[\\u4e00-\\u9fff\\u3040-\\u30ff\\uac00-\\ud7a3]");
    private static final Pattern CODE_PATTERN = Pattern.compile("[{}()\\[\\];=<>|&!+\\-*/^~]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /** 每 token 的平均字符数（各类型） */
    private static final double CHARS_PER_TOKEN_ENGLISH = 4.0;
    private static final double CHARS_PER_TOKEN_CODE = 3.5;
    private static final double CHARS_PER_TOKEN_CJK = 1.5;
    private static final double CHARS_PER_TOKEN_JSON = 3.0;

    /**
     * 估算文本的 token 数量。
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        int totalChars = text.length();
        if (totalChars == 0) return 0;

        // 分析文本组成
        int cjkChars = countCJK(text);
        int codeChars = countCodeChars(text);
        int remainingChars = totalChars - cjkChars - codeChars;

        // 按类型估算
        double cjkTokens = cjkChars / CHARS_PER_TOKEN_CJK;
        double codeTokens = codeChars / CHARS_PER_TOKEN_CODE;
        double textTokens = remainingChars / CHARS_PER_TOKEN_ENGLISH;

        // 考虑 JSON 结构
        if (looksLikeJson(text)) {
            return (int) Math.ceil(totalChars / CHARS_PER_TOKEN_JSON);
        }

        int estimated = (int) Math.ceil(cjkTokens + codeTokens + textTokens);

        // 每段文本至少 1 个 token
        return Math.max(1, estimated);
    }

    /**
     * 估算消息列表的总 token 数（包括消息结构开销）。
     */
    public int estimateMessageTokens(String role, String content) {
        // 每条消息有 ~4 token 的结构开销（role 标记、分隔符等）
        int contentTokens = estimateTokens(content);
        return contentTokens + 4;
    }

    /**
     * 估算系统提示词 token 数。
     */
    public int estimateSystemPromptTokens(String systemPrompt) {
        // 系统提示词通常有额外的缓存标记开销
        return estimateTokens(systemPrompt) + 10;
    }

    /**
     * 估算工具定义的 token 数。
     */
    public int estimateToolDefinitionTokens(String toolName, String description, String inputSchema) {
        int tokens = estimateTokens(toolName) + estimateTokens(description);
        if (inputSchema != null) {
            tokens += estimateTokens(inputSchema);
        }
        // 工具定义的结构开销
        tokens += 20;
        return tokens;
    }

    /**
     * 将 token 数转换为近似费用。
     *
     * @param inputTokens  输入 token 数
     * @param outputTokens 输出 token 数
     * @param model        模型名称
     * @return 估计费用（美元）
     */
    public double estimateCost(long inputTokens, long outputTokens, String model) {
        double inputRate;
        double outputRate;

        if (model != null && model.toLowerCase().contains("opus")) {
            inputRate = 15.0;   // $15/M
            outputRate = 75.0;  // $75/M
        } else if (model != null && model.toLowerCase().contains("haiku")) {
            inputRate = 0.25;   // $0.25/M
            outputRate = 1.25;  // $1.25/M
        } else {
            // Default: Sonnet pricing
            inputRate = 3.0;    // $3/M
            outputRate = 15.0;  // $15/M
        }

        return (inputTokens / 1_000_000.0 * inputRate)
                + (outputTokens / 1_000_000.0 * outputRate);
    }

    /**
     * 格式化 token 数量。
     */
    public String formatTokenCount(int tokens) {
        if (tokens >= 1_000_000) return String.format("%.1fM", tokens / 1_000_000.0);
        if (tokens >= 1_000) return String.format("%.1fK", tokens / 1_000.0);
        return String.valueOf(tokens);
    }

    // ==================== 内部方法 ====================

    private int countCJK(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u4e00' && c <= '\u9fff')    // CJK Unified
                    || (c >= '\u3040' && c <= '\u30ff')  // Hiragana + Katakana
                    || (c >= '\uac00' && c <= '\ud7a3')) { // Hangul
                count++;
            }
        }
        return count;
    }

    private int countCodeChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ("{}()[];=<>|&!+-*/^~@#$%".indexOf(c) >= 0) {
                count++;
            }
        }
        return count;
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}
