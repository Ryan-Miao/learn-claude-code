package com.demo.learn.web;

import com.demo.learn.core.config.AiConfig;
import com.demo.learn.core.tools.BashTool;
import com.demo.learn.core.tools.EditFileTool;
import com.demo.learn.core.tools.ReadFileTool;
import com.demo.learn.core.tools.WriteFileTool;
import com.demo.learn.s03.TodoManager;
import com.demo.learn.s04.SubagentTool;
import com.demo.learn.s05.SkillLoader;
import com.demo.learn.s07.TaskManager;
import com.demo.learn.s08.BackgroundManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Path;
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

    private static final String WORK_DIR = System.getProperty("user.dir");

    private static final Map<String, AgentDef> AGENTS = new ConcurrentHashMap<>();

    /** Shared AiConfig for tools that need it (e.g., SubagentTool). */
    private static AiConfig aiConfig;

    public static void setAiConfig(AiConfig config) {
        aiConfig = config;
    }

    static {
        // --- S01: Agent Loop (bash only) ---
        AGENTS.put("s01", new AgentDef("s01", "S01 Agent Loop",
                "You are a coding agent at " + WORK_DIR
                        + ". Use bash to solve tasks. Act, don't explain.",
                true));

        // --- S02: Tool Use (bash + read/write/edit) ---
        AGENTS.put("s02", new AgentDef("s02", "S02 Tool Use",
                "You are a coding agent at " + WORK_DIR
                        + ". Use bash, read_file, write_file, and edit_file to solve tasks.",
                true));

        // --- S03: TodoWrite (s02 tools + todo) ---
        AGENTS.put("s03", new AgentDef("s03", "S03 TodoWrite",
                "You are a coding agent at " + WORK_DIR
                        + ". Plan your work with todos, then execute. "
                        + "Track progress using the todo tool.",
                true));

        // --- S04: Subagent (s02 tools + subagent) ---
        AGENTS.put("s04", new AgentDef("s04", "S04 Subagent",
                "You are a coding agent at " + WORK_DIR
                        + ". Break big tasks into subtasks. "
                        + "Use the subagent tool to delegate work with isolated context.",
                true));

        // --- S05: Skill Loading (s02 tools + loadSkill) ---
        AGENTS.put("s05", new AgentDef("s05", "S05 Skill Loading",
                "You are a coding agent at " + WORK_DIR
                        + ". Load domain knowledge on demand using the loadSkill tool. "
                        + "Skills available: pdf, code-review. "
                        + "Call loadSkill when you need specialized instructions.",
                true));

        // --- S06: Context Compact (s02 tools, compact is advisor not tool) ---
        AGENTS.put("s06", new AgentDef("s06", "S06 Context Compact",
                "You are a coding agent at " + WORK_DIR
                        + ". Use all available tools. When context gets long, "
                        + "summarize old results to keep working efficiently.",
                true));

        // --- S07: Task System (s02 tools + task manager) ---
        AGENTS.put("s07", new AgentDef("s07", "S07 Task System",
                "You are a coding agent at " + WORK_DIR
                        + ". Use task tools to plan and track work. "
                        + "Break big goals into small tasks, order them, and persist to disk.",
                true));

        // --- S08: Background Tasks (s02 tools + background) ---
        AGENTS.put("s08", new AgentDef("s08", "S08 Background Tasks",
                "You are a coding agent at " + WORK_DIR
                        + ". Use backgroundRun for slow operations. "
                        + "Keep thinking while background tasks run.",
                true));

        // --- S09-S12: Multi-agent chapters (basic tool set, full team tools need runtime orchestration) ---
        AGENTS.put("s09", new AgentDef("s09", "S09 Agent Teams",
                "You are a coding agent at " + WORK_DIR
                        + ". (Web mode: basic tools only. Full team features require CLI mode.)",
                true));

        AGENTS.put("s10", new AgentDef("s10", "S10 Team Protocols",
                "You are a coding agent at " + WORK_DIR
                        + ". (Web mode: basic tools only. Full protocol features require CLI mode.)",
                true));

        AGENTS.put("s11", new AgentDef("s11", "S11 Autonomous Agents",
                "You are a coding agent at " + WORK_DIR
                        + ". (Web mode: basic tools only. Full autonomous features require CLI mode.)",
                true));

        AGENTS.put("s12", new AgentDef("s12", "S12 Worktree Isolation",
                "You are a coding agent at " + WORK_DIR
                        + ". (Web mode: basic tools only. Full worktree features require CLI mode.)",
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
        List<Object> toolObjects = new ArrayList<>();

        switch (agentId) {
            case "s01" -> toolObjects.add(new BashTool());

            case "s02", "s06" -> toolObjects.addAll(List.of(
                    new BashTool(), new ReadFileTool(), new WriteFileTool(), new EditFileTool()));

            case "s03" -> {
                toolObjects.addAll(List.of(
                        new BashTool(), new ReadFileTool(), new WriteFileTool(), new EditFileTool()));
                toolObjects.add(new TodoManager());
            }

            case "s04" -> {
                toolObjects.addAll(List.of(
                        new BashTool(), new ReadFileTool(), new WriteFileTool(), new EditFileTool()));
                if (aiConfig != null) {
                    toolObjects.add(new SubagentTool(aiConfig));
                }
            }

            case "s05" -> {
                toolObjects.addAll(List.of(
                        new BashTool(), new ReadFileTool(), new WriteFileTool(), new EditFileTool()));
                Path skillsDir = Path.of(WORK_DIR, "skills");
                toolObjects.add(new SkillLoader(skillsDir));
            }

            case "s07" -> {
                toolObjects.addAll(List.of(
                        new BashTool(), new ReadFileTool(), new WriteFileTool(), new EditFileTool()));
                Path tasksDir = Path.of(WORK_DIR, ".tasks");
                toolObjects.add(new TaskManager(tasksDir));
            }

            case "s08" -> {
                toolObjects.addAll(List.of(
                        new BashTool(), new ReadFileTool(), new WriteFileTool(), new EditFileTool()));
                toolObjects.add(new BackgroundManager());
            }

            // S09-S12: basic tool set in web mode
            default -> toolObjects.addAll(List.of(
                    new BashTool(), new ReadFileTool(), new WriteFileTool(), new EditFileTool()));
        }

        // Convert all @Tool-annotated objects to ToolCallbacks, wrap with capture
        for (Object tool : toolObjects) {
            Arrays.stream(ToolCallbacks.from(tool))
                    .forEach(cb -> callbacks.add(new CaptureToolCallback(cb, capturedCalls)));
        }

        return callbacks;
    }
}
