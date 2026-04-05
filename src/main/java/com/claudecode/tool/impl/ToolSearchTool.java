package com.claudecode.tool.impl;

import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;
import com.claudecode.tool.ToolRegistry;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具搜索工具 —— 对应 claude-code 中 /tools 命令功能。
 * <p>
 * 在 ToolRegistry 中搜索已注册的工具，按名称或描述关键字匹配。
 * 用于帮助 LLM 发现可用工具。
 */
public class ToolSearchTool implements Tool {

    private static final String TOOL_REGISTRY_KEY = "TOOL_REGISTRY";

    @Override
    public String name() {
        return "ToolSearch";
    }

    @Override
    public String description() {
        return """
            Search for available tools by name or keyword. Returns matching tool names and \
            their descriptions. Use this when you need to find which tools are available for \
            a specific task, or when the user asks about available capabilities.""";
    }

    @Override
    public String inputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "Search query to match against tool names and descriptions. Empty string lists all tools."
                }
              },
              "required": ["query"]
            }""";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String query = (String) input.get("query");
        if (query == null) query = "";
        String queryLower = query.toLowerCase().trim();

        ToolRegistry registry = context.getOrDefault(TOOL_REGISTRY_KEY, null);
        if (registry == null) {
            return "Error: ToolRegistry is not available";
        }

        List<Tool> allTools = registry.getTools();
        List<Tool> matches;

        if (queryLower.isEmpty()) {
            matches = allTools;
        } else {
            matches = allTools.stream()
                    .filter(t -> t.name().toLowerCase().contains(queryLower)
                            || t.description().toLowerCase().contains(queryLower))
                    .collect(Collectors.toList());
        }

        if (matches.isEmpty()) {
            return String.format("""
                {"query": "%s", "total_tools": %d, "matches": 0, \
                "message": "No tools matched the query."}""",
                    escapeJson(query), allTools.size());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d tool(s)", matches.size()));
        if (!queryLower.isEmpty()) {
            sb.append(" matching \"").append(query).append("\"");
        }
        sb.append(" (").append(allTools.size()).append(" total):\n\n");

        for (Tool t : matches) {
            sb.append("• **").append(t.name()).append("**");
            if (t.isReadOnly()) sb.append(" [read-only]");
            sb.append("\n");
            // Truncate description to first 120 chars for overview
            String desc = t.description().strip();
            if (desc.length() > 120) {
                desc = desc.substring(0, 117) + "...";
            }
            sb.append("  ").append(desc).append("\n\n");
        }

        return sb.toString().stripTrailing();
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        return "🔍 Searching tools: " + input.getOrDefault("query", "*");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
