package com.demo.learn.web;

import com.demo.learn.core.tools.BashTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of available agents with their system prompts and tools.
 */
public class AgentRegistry {

    public record AgentDef(String agentId, String name, String systemPrompt, boolean enabled) {}

    private static final Map<String, AgentDef> AGENTS = new ConcurrentHashMap<>();

    static {
        AGENTS.put("s01", new AgentDef("s01", "S01 Agent Loop",
                "You are a coding agent at " + System.getProperty("user.dir")
                        + ". Use bash to solve tasks. Act, don't explain.",
                true));
    }

    public static List<AgentDef> listAgents() {
        return new ArrayList<>(AGENTS.values());
    }

    public static AgentDef getAgent(String agentId) {
        return AGENTS.get(agentId);
    }

    /**
     * Build tool callbacks for an agent, wrapped with CaptureToolCallback
     * to record input/output into the provided list.
     */
    public static List<ToolCallback> buildToolCallbacks(String agentId, List<CaptureToolCallback.CapturedToolCall> capturedCalls) {
        AgentDef agent = AGENTS.get(agentId);
        if (agent == null) return List.of();

        List<ToolCallback> callbacks = new ArrayList<>();
        // S01 has only BashTool
        if ("s01".equals(agentId)) {
            ToolCallback[] bashCallbacks = ToolCallbacks.from(new BashTool());
            Arrays.stream(bashCallbacks)
                    .forEach(cb -> callbacks.add(new CaptureToolCallback(cb, capturedCalls)));
        }
        return callbacks;
    }
}
