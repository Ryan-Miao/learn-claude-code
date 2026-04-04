"use client";

import { AgentInfo } from "@/lib/chat-runtime";

interface AgentSelectorProps {
  agents: AgentInfo[];
  selected: string;
  onChange: (agentId: string) => void;
}

export function AgentSelector({ agents, selected, onChange }: AgentSelectorProps) {
  return (
    <select
      value={selected}
      onChange={(e) => onChange(e.target.value)}
      className="rounded-md border border-zinc-300 bg-white px-3 py-1.5 text-sm
                 dark:border-zinc-700 dark:bg-zinc-800"
    >
      {agents.map((a) => (
        <option key={a.agentId} value={a.agentId} disabled={!a.enabled}>
          {a.name} {!a.enabled && "(coming soon)"}
        </option>
      ))}
    </select>
  );
}
