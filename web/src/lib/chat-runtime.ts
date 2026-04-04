const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface ChatRequest {
  message: string;
  agentId: string;
  sessionId: string;
}

export interface ToolCall {
  name: string;
  input: string;
  output: string;
}

export interface ChatResponse {
  text: string;
  thinking: string;
  toolCalls: ToolCall[];
}

export async function sendChatMessage(
  request: ChatRequest
): Promise<ChatResponse> {
  const res = await fetch(`${API_URL}/api/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "Request failed" }));
    throw new Error(err.error || `HTTP ${res.status}`);
  }

  return res.json();
}

export interface AgentInfo {
  agentId: string;
  name: string;
  enabled: boolean;
}

export async function fetchAgents(): Promise<AgentInfo[]> {
  const res = await fetch(`${API_URL}/api/agents`);
  if (!res.ok) throw new Error("Failed to fetch agents");
  return res.json();
}
