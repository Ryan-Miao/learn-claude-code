package com.demo.learn.core;

/**
 * API 错误分类工具和异常定义。
 * <p>
 * Spring AI 2.0 内置重试机制，此处的分类主要用于 AgentRunner 的错误提示。
 */
public class ApiException extends RuntimeException {

    private final ErrorType errorType;

    public ApiException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public enum ErrorType {
        TIMEOUT,           // 请求超时 - 可重试
        IO_ERROR,          // 网络错误 - 可重试
        SERVER_ERROR,      // 5xx - 可重试
        AUTH_FAILED,       // 401 - 不可重试
        FORBIDDEN,         // 403 - 不可重试
        NOT_FOUND,         // 404 - 不可重试
        RATE_LIMITED,      // 429 - 不可重试
        CLIENT_ERROR,      // 其他 4xx - 不可重试
        UNKNOWN;           // 未知错误 - 不可重试

        public boolean isRetryable() {
            return this == TIMEOUT || this == IO_ERROR || this == SERVER_ERROR;
        }
    }
}
