"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import {
  sendChatMessage,
  fetchAgents,
  type AgentInfo,
  type ToolCall,
} from "@/lib/chat-runtime";
import { AgentSelector } from "./agent-selector";

interface Message {
  role: "user" | "assistant";
  text: string;
  thinking?: string;
  toolCalls?: ToolCall[];
  error?: string;
}

export function ChatPage() {
  const [agents, setAgents] = useState<AgentInfo[]>([]);
  const [selectedAgent, setSelectedAgent] = useState("s01");
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [sessionId] = useState(() => crypto.randomUUID());
  const [expandedTools, setExpandedTools] = useState<Set<number>>(new Set());
  const [expandedThinking, setExpandedThinking] = useState<Set<number>>(
    new Set()
  );
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchAgents().then(setAgents).catch(() =>
      setAgents([
        { agentId: "s01", name: "S01 Agent Loop", enabled: true },
      ])
    );
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const toggleTool = useCallback((idx: number) => {
    setExpandedTools((prev) => {
      const next = new Set(prev);
      next.has(idx) ? next.delete(idx) : next.add(idx);
      return next;
    });
  }, []);

  const toggleThinking = useCallback((idx: number) => {
    setExpandedThinking((prev) => {
      const next = new Set(prev);
      next.has(idx) ? next.delete(idx) : next.add(idx);
      return next;
    });
  }, []);

  const handleSend = useCallback(async () => {
    const trimmed = input.trim();
    if (!trimmed || loading) return;

    const userMsg: Message = { role: "user", text: trimmed };
    setMessages((prev) => [...prev, userMsg]);
    setInput("");
    setLoading(true);

    try {
      const res = await sendChatMessage({
        message: trimmed,
        agentId: selectedAgent,
        sessionId,
      });
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          text: res.text,
          thinking: res.thinking || undefined,
          toolCalls:
            res.toolCalls && res.toolCalls.length > 0
              ? res.toolCalls
              : undefined,
        },
      ]);
    } catch (e) {
      setMessages((prev) => [
        ...prev,
        { role: "assistant", text: "", error: (e as Error).message },
      ]);
    } finally {
      setLoading(false);
    }
  }, [input, loading, selectedAgent, sessionId]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend]
  );

  return (
    <div className="flex h-[calc(100vh-8rem)] flex-col">
      {/* Header */}
      <div className="flex items-center gap-4 border-b border-zinc-200 pb-3 dark:border-zinc-700">
        <h1 className="text-lg font-semibold">Chat</h1>
        <AgentSelector
          agents={agents}
          selected={selectedAgent}
          onChange={setSelectedAgent}
        />
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto py-4 space-y-4">
        {messages.map((msg, i) => (
          <div
            key={i}
            className={`flex ${
              msg.role === "user" ? "justify-end" : "justify-start"
            }`}
          >
            <div
              className={`max-w-[80%] rounded-lg px-4 py-2.5 text-sm ${
                msg.role === "user"
                  ? "bg-blue-600 text-white"
                  : "bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100"
              }`}
            >
              {/* Error */}
              {msg.error && (
                <p className="text-red-500">Error: {msg.error}</p>
              )}

              {/* Thinking block */}
              {msg.thinking && (
                <div className="mb-2">
                  <button
                    onClick={() => toggleThinking(i)}
                    className="flex items-center gap-1 text-xs text-zinc-500 hover:text-zinc-700 dark:text-zinc-400"
                  >
                    <span>{expandedThinking.has(i) ? "\u25BC" : "\u25B6"}</span>
                    Thinking...
                  </button>
                  {expandedThinking.has(i) && (
                    <pre className="mt-1 whitespace-pre-wrap rounded bg-zinc-200 p-2 text-xs text-zinc-600 dark:bg-zinc-700 dark:text-zinc-300">
                      {msg.thinking}
                    </pre>
                  )}
                </div>
              )}

              {/* Tool calls */}
              {msg.toolCalls?.map((tc, j) => {
                const toolIdx = i * 100 + j;
                return (
                  <div key={j} className="mb-2">
                    <button
                      onClick={() => toggleTool(toolIdx)}
                      className="flex items-center gap-1 text-xs text-amber-600 hover:text-amber-700 dark:text-amber-400"
                    >
                      <span>
                        {expandedTools.has(toolIdx) ? "\u25BC" : "\u25B6"}
                      </span>
                      {"\uD83D\uDD27"} {tc.name}:{" "}
                      {tc.input.substring(0, 60)}
                      {tc.input.length > 60 ? "..." : ""}
                    </button>
                    {expandedTools.has(toolIdx) && (
                      <pre className="mt-1 whitespace-pre-wrap rounded bg-zinc-200 p-2 text-xs text-zinc-600 dark:bg-zinc-700 dark:text-zinc-300">
                        {tc.output}
                      </pre>
                    )}
                  </div>
                );
              })}

              {/* Text */}
              {msg.text && (
                <p className="whitespace-pre-wrap">{msg.text}</p>
              )}
            </div>
          </div>
        ))}
        {loading && (
          <div className="text-sm text-zinc-400">Thinking...</div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="flex gap-2 border-t border-zinc-200 pt-3 dark:border-zinc-700">
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type a message..."
          rows={1}
          className="flex-1 resize-none rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm
                     dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
        />
        <button
          onClick={handleSend}
          disabled={loading || !input.trim()}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm text-white
                     disabled:opacity-50 hover:bg-blue-700"
        >
          Send
        </button>
      </div>
    </div>
  );
}
