package com.claudecode.tool.impl;

import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 子 Agent 工具 —— 对应 claude-code/src/tools/agent/AgentTool.ts。
 * <p>
 * 创建一个独立的子 Agent 来处理复杂的子任务。子 Agent 拥有独立的消息历史，
 * 但共享工具集和上下文环境。适用于：
 * <ul>
 *   <li>需要独立上下文的子任务（如分析另一个文件）</li>
 *   <li>并行处理多个任务</li>
 *   <li>隔离风险操作</li>
 * </ul>
 * <p>
 * 注意：子 Agent 使用主 Agent 的 ChatModel 和工具集，
 * 通过 ToolContext 中的 "agentLoop.factory" 获取 AgentLoop 工厂方法。
 */
public class AgentTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(AgentTool.class);

    /** ToolContext 中存储 AgentLoop 工厂的键名 */
    public static final String AGENT_FACTORY_KEY = "__agent_factory__";

    @Override
    public String name() {
        return "Agent";
    }

    @Override
    public String description() {
        return """
            Launch a sub-agent to handle a complex, multi-step task autonomously. \
            The sub-agent has its own conversation context but shares tools and environment.

            WHEN TO USE:
            - Complex tasks requiring focused attention or multiple steps
            - Parallelizing independent investigations (launch multiple agents)
            - Protecting the main context from excessive tool output (search results, logs)
            - Tasks that need isolated context (analyzing a separate module/file)

            WHEN NOT TO USE:
            - Simple, single-step operations (use the tool directly instead)
            - Tasks where you need the result immediately in your context
            - When you would just be delegating a single tool call

            IMPORTANT:
            - Provide complete, self-contained prompts — the agent has no conversation history.
            - Do NOT duplicate work that a sub-agent is already doing.
            - The agent will return a concise result; it cannot ask follow-up questions.
            - For simple searches (finding a file, checking a function), use Grep/Glob directly.""";
    }

    @Override
    public String inputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "prompt": {
                  "type": "string",
                  "description": "The task description / prompt for the sub-agent"
                },
                "context": {
                  "type": "string",
                  "description": "Additional context or instructions (optional)"
                }
              },
              "required": ["prompt"]
            }""";
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String prompt = (String) input.get("prompt");
        String additionalContext = (String) input.getOrDefault("context", "");

        if (prompt == null || prompt.isBlank()) {
            return "Error: 'prompt' is required";
        }

        // 从 ToolContext 获取 AgentLoop 工厂方法
        @SuppressWarnings("unchecked")
        java.util.function.Function<String, String> agentFactory =
                context.getOrDefault(AGENT_FACTORY_KEY, null);

        if (agentFactory == null) {
            log.warn("AgentTool: Agent factory not configured, cannot create sub-agent");
            return "Error: Sub-agent capability is not configured. "
                   + "The Agent tool requires an agent factory to be registered in the ToolContext.";
        }

        // 构建完整的子 Agent 提示
        String fullPrompt = buildSubAgentPrompt(prompt, additionalContext);

        log.info("Starting sub-agent, task: {}", truncate(prompt, 80));

        try {
            String result = agentFactory.apply(fullPrompt);
            log.info("Sub-agent completed, result length: {} chars", result.length());
            return result;
        } catch (Exception e) {
            log.debug("Sub-agent execution failed", e);
            return "Error: Sub-agent failed: " + e.getMessage();
        }
    }

    /**
     * 构建子 Agent 的完整提示词。
     * 参考 TS 版 DEFAULT_AGENT_PROMPT + enhanceSystemPromptWithEnvDetails。
     */
    private String buildSubAgentPrompt(String prompt, String additionalContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a sub-agent for a CLI coding assistant. Given the user's task, you should use \
                the tools available to complete the task. Complete the task fully — don't gold-plate, \
                but don't leave it half-done. When you complete the task, respond with a concise report \
                covering what was done and any key findings — the caller will relay this to the user, \
                so it only needs the essentials.

                Notes:
                - In your final response, share file paths (always absolute, never relative) that are \
                relevant to the task. Include code snippets only when the exact text is load-bearing.
                - Avoid using emojis in communication.
                - Do not use a colon before tool calls.

                """);

        sb.append("## Task\n");
        sb.append(prompt);

        if (additionalContext != null && !additionalContext.isBlank()) {
            sb.append("\n\n## Additional Context\n");
            sb.append(additionalContext);
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        String prompt = (String) input.getOrDefault("prompt", "");
        if (prompt.length() > 40) {
            prompt = prompt.substring(0, 37) + "...";
        }
        return "🤖 Sub-agent: " + prompt;
    }
}
