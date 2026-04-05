package com.claudecode.tool.impl;

import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Config 工具 —— 获取或设置配置值，持久化到 JSON 文件。
 * <p>
 * 属于 P2 优先级的辅助工具。存储后端优先级：
 * </p>
 * <ol>
 *   <li>ToolContext 中的内存缓存（CONFIG_STORE）—— 快速读写</li>
 *   <li>JSON 文件持久化（~/.claude-code/config.json）—— 跨会话保持</li>
 *   <li>回退到 {@link System#getProperty} —— 兜底读取</li>
 * </ol>
 *
 * <h3>参数</h3>
 * <ul>
 *   <li><b>action</b>（必填）—— "get"、"set" 或 "list"</li>
 *   <li><b>key</b>（get/set 时必填）—— 配置键名</li>
 *   <li><b>value</b>（set 时必填）—— 配置值</li>
 * </ul>
 */
public class ConfigTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ConfigTool.class);

    /** ToolContext 中配置存储的键名 */
    private static final String CONFIG_STORE_KEY = "CONFIG_STORE";

    /** 配置文件路径 */
    private static final Path CONFIG_FILE = Path.of(
            System.getProperty("user.home"), ".claude-code", "config.json");

    @Override
    public String name() {
        return "Config";
    }

    @Override
    public String description() {
        return """
            Get, set, or list configuration values. Configuration persists across sessions \
            in ~/.claude-code/config.json.

            Available settings include:
             - language: Preferred response language (e.g., "zh-CN", "en")
             - theme: Color theme (light/dark)
             - model: AI model to use
             - verbose: Enable verbose output (true/false)
             - timeout: Default command timeout in seconds
             - permissions: Permission mode (ask/auto/deny)

            Use action 'get' to read, 'set' to change, 'list' to see all settings.""";
    }

    @Override
    public String inputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "action": {
                      "type": "string",
                      "description": "Action type: get, set, or list",
                      "enum": ["get", "set", "list"]
                    },
                    "key": {
                      "type": "string",
                      "description": "Configuration key name (required for get/set)"
                    },
                    "value": {
                      "type": "string",
                      "description": "Configuration value (required for set operation)"
                    }
                  },
                  "required": ["action"]
                }""";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String action = (String) input.get("action");
        if (action == null || action.isBlank()) {
            return errorJson("Parameter 'action' is required, valid values: get, set, list");
        }
        action = action.trim().toLowerCase();

        // 获取或初始化配置存储（从文件加载）
        ConcurrentHashMap<String, String> configStore = getOrInitStore(context);

        return switch (action) {
            case "get" -> {
                String key = (String) input.get("key");
                if (key == null || key.isBlank()) {
                    yield errorJson("'get' action requires 'key' parameter");
                }
                yield executeGet(key, configStore);
            }
            case "set" -> {
                String key = (String) input.get("key");
                if (key == null || key.isBlank()) {
                    yield errorJson("'set' action requires 'key' parameter");
                }
                yield executeSet(key, input, configStore);
            }
            case "list" -> executeList(configStore);
            default -> errorJson("Invalid action: '" + action + "'. Valid values: get, set, list");
        };
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        String action = (String) input.getOrDefault("action", "?");
        String key = (String) input.getOrDefault("key", "?");
        if ("list".equalsIgnoreCase(action)) {
            return "⚙️ Listing all config";
        }
        if ("set".equalsIgnoreCase(action)) {
            return "⚙️ Setting config: " + key;
        }
        return "⚙️ Getting config: " + key;
    }

    /* ------------------------------------------------------------------ */
    /*  get / set / list 具体实现                                           */
    /* ------------------------------------------------------------------ */

    private String executeGet(String key, ConcurrentHashMap<String, String> configStore) {
        String value = configStore.get(key);
        if (value == null) {
            value = System.getProperty(key);
        }

        if (value == null) {
            return """
                    {"action": "get", "key": "%s", "value": null, "found": false, \
                    "message": "Config key '%s' not found"}"""
                    .formatted(escapeJson(key), escapeJson(key));
        }

        return """
                {"action": "get", "key": "%s", "value": "%s", "found": true}"""
                .formatted(escapeJson(key), escapeJson(value));
    }

    private String executeSet(String key, Map<String, Object> input,
                              ConcurrentHashMap<String, String> configStore) {
        String value = (String) input.get("value");
        if (value == null) {
            return errorJson("'set' action requires 'value' parameter");
        }

        String oldValue = configStore.get(key);
        configStore.put(key, value);

        // 持久化到文件
        persistToFile(configStore);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"action\": \"set\", \"key\": \"").append(escapeJson(key));
        sb.append("\", \"value\": \"").append(escapeJson(value)).append("\"");
        if (oldValue != null) {
            sb.append(", \"previous_value\": \"").append(escapeJson(oldValue)).append("\"");
        }
        sb.append(", \"success\": true}");
        return sb.toString();
    }

    private String executeList(ConcurrentHashMap<String, String> configStore) {
        if (configStore.isEmpty()) {
            return "{\"action\": \"list\", \"count\": 0, \"message\": \"No configuration set\"}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"action\": \"list\", \"count\": ").append(configStore.size());
        sb.append(", \"settings\": {");
        boolean first = true;
        for (var entry : new java.util.TreeMap<>(configStore).entrySet()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\": \"")
                    .append(escapeJson(entry.getValue())).append("\"");
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    /* ------------------------------------------------------------------ */
    /*  持久化 — JSON 文件读写                                              */
    /* ------------------------------------------------------------------ */

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, String> getOrInitStore(ToolContext context) {
        ConcurrentHashMap<String, String> store = context.getOrDefault(CONFIG_STORE_KEY, null);
        if (store != null) {
            return store;
        }

        // 从文件加载
        store = loadFromFile();
        context.set(CONFIG_STORE_KEY, store);
        return store;
    }

    /**
     * 从 JSON 文件加载配置。
     * 使用简单的手动 JSON 解析，避免引入额外依赖。
     */
    private ConcurrentHashMap<String, String> loadFromFile() {
        ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

        if (!Files.exists(CONFIG_FILE)) {
            return store;
        }

        try {
            String json = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8).trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                // Simple JSON parsing for flat key-value pairs
                String inner = json.substring(1, json.length() - 1).trim();
                if (!inner.isEmpty()) {
                    parseJsonPairs(inner, store);
                }
            }
            log.debug("Loaded {} config entries from {}", store.size(), CONFIG_FILE);
        } catch (IOException e) {
            log.warn("Failed to load config from {}: {}", CONFIG_FILE, e.getMessage());
        }

        return store;
    }

    /**
     * 持久化配置到 JSON 文件。
     */
    private void persistToFile(ConcurrentHashMap<String, String> store) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());

            StringBuilder json = new StringBuilder("{\n");
            boolean first = true;
            for (var entry : new java.util.TreeMap<>(store).entrySet()) {
                if (!first) json.append(",\n");
                json.append("  \"").append(escapeJson(entry.getKey()))
                        .append("\": \"").append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
            json.append("\n}");

            Files.writeString(CONFIG_FILE, json.toString(), StandardCharsets.UTF_8);
            log.debug("Persisted {} config entries to {}", store.size(), CONFIG_FILE);
        } catch (IOException e) {
            log.warn("Failed to persist config to {}: {}", CONFIG_FILE, e.getMessage());
        }
    }

    /**
     * 简单解析 JSON 键值对（仅支持字符串值的扁平对象）。
     */
    private void parseJsonPairs(String inner, Map<String, String> store) {
        int i = 0;
        while (i < inner.length()) {
            // Find key
            int keyStart = inner.indexOf('"', i);
            if (keyStart < 0) break;
            int keyEnd = findClosingQuote(inner, keyStart + 1);
            if (keyEnd < 0) break;
            String key = unescapeJson(inner.substring(keyStart + 1, keyEnd));

            // Find colon
            int colon = inner.indexOf(':', keyEnd + 1);
            if (colon < 0) break;

            // Find value
            int valStart = inner.indexOf('"', colon + 1);
            if (valStart < 0) break;
            int valEnd = findClosingQuote(inner, valStart + 1);
            if (valEnd < 0) break;
            String value = unescapeJson(inner.substring(valStart + 1, valEnd));

            store.put(key, value);
            i = valEnd + 1;
        }
    }

    private int findClosingQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '\\') { i++; continue; }
            if (s.charAt(i) == '"') return i;
        }
        return -1;
    }

    private String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\t", "\t");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String errorJson(String message) {
        return "{\"error\": true, \"message\": \"%s\"}".formatted(escapeJson(message));
    }
}
