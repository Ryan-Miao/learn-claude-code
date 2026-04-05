"use client";

import { useState } from "react";
import type { ApiRoundTrip, HttpTraffic } from "@/lib/chat-runtime";
import { NetworkPanel } from "./network-panel";

interface MonitorPanelProps {
  roundTrips: ApiRoundTrip[];
  httpTraffic?: HttpTraffic[];
}

type MonitorTab = "overview" | "network";

export function MonitorPanel({ roundTrips, httpTraffic }: MonitorPanelProps) {
  const [activeTab, setActiveTab] = useState<MonitorTab>("overview");

  if (roundTrips.length === 0) return null;

  const hasTraffic = httpTraffic && httpTraffic.length > 0;

  return (
    <div className="space-y-2">
      {/* Tab bar */}
      <div className="flex gap-1 border-b border-zinc-200 dark:border-zinc-700">
        <button
          className={`px-3 py-1.5 text-xs font-medium transition-colors ${
            activeTab === "overview"
              ? "text-blue-600 border-b-2 border-blue-600 dark:text-blue-400 dark:border-blue-400"
              : "text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-200"
          }`}
          onClick={() => setActiveTab("overview")}
        >
          Overview
        </button>
        <button
          className={`px-3 py-1.5 text-xs font-medium transition-colors ${
            activeTab === "network"
              ? "text-blue-600 border-b-2 border-blue-600 dark:text-blue-400 dark:border-blue-400"
              : "text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-200"
          } ${!hasTraffic ? "opacity-40 cursor-not-allowed" : ""}`}
          onClick={() => hasTraffic && setActiveTab("network")}
          disabled={!hasTraffic}
        >
          Network {hasTraffic ? `(${httpTraffic!.length})` : ""}
        </button>
      </div>

      {/* Tab content */}
      {activeTab === "overview" && <OverviewPanel roundTrips={roundTrips} />}
      {activeTab === "network" && hasTraffic && (
        <NetworkPanel traffic={httpTraffic!} />
      )}
    </div>
  );
}

/** Existing MonitorPanel content, extracted as OverviewPanel */
function OverviewPanel({ roundTrips }: { roundTrips: ApiRoundTrip[] }) {
  return (
    <div className="space-y-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-900">
      {/* Summary */}
      <div className="flex items-center justify-between border-b border-zinc-200 pb-2 dark:border-zinc-700">
        <span className="text-xs font-medium text-zinc-600 dark:text-zinc-300">
          API Round Trips
        </span>
        <span className="font-mono text-xs text-zinc-500">
          {roundTrips.reduce((sum, rt) => sum + rt.response.inputTokens, 0)}{" "}
          in /{" "}
          {roundTrips.reduce((sum, rt) => sum + rt.response.outputTokens, 0)}{" "}
          out /{" "}
          {roundTrips.reduce((sum, rt) => sum + rt.response.durationMs, 0)}ms
        </span>
      </div>

      {/* Per-round details */}
      {roundTrips.map((rt) => (
        <div
          key={rt.round}
          className="rounded border border-zinc-200 bg-white p-2 dark:border-zinc-600 dark:bg-zinc-800"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-zinc-700 dark:text-zinc-200">
              Round {rt.round}
            </span>
            <span className="font-mono text-xs text-zinc-500">
              {rt.request.model} &middot; {rt.response.inputTokens} in /{" "}
              {rt.response.outputTokens} out &middot; {rt.response.durationMs}ms
            </span>
          </div>

          <div className="mt-2">
            <span className="text-xs font-medium text-zinc-500">Request</span>
            {rt.request.systemPrompt && (
              <pre className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-blue-50 p-1.5 text-xs text-blue-800 dark:bg-blue-900/30 dark:text-blue-200">
                {rt.request.systemPrompt}
              </pre>
            )}
            {rt.request.messages.map((msg, i) => (
              <pre
                key={i}
                className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-blue-50 p-1.5 text-xs text-blue-800 dark:bg-blue-900/30 dark:text-blue-200"
              >
                {msg}
              </pre>
            ))}
            {rt.request.tools.length > 0 && (
              <div className="mt-1 text-xs text-zinc-500">
                Tools: {rt.request.tools.join(", ")}
              </div>
            )}
          </div>

          <div className="mt-2">
            <span className="text-xs font-medium text-zinc-500">Response</span>
            {rt.response.thinking && (
              <pre className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-purple-50 p-1.5 text-xs text-purple-800 dark:bg-purple-900/30 dark:text-purple-200">
                {rt.response.thinking}
              </pre>
            )}
            {rt.response.text && (
              <pre className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-green-50 p-1.5 text-xs text-green-800 dark:bg-green-900/30 dark:text-green-200">
                {rt.response.text}
              </pre>
            )}
            {rt.response.toolCalls.map((tc, i) => (
              <pre
                key={i}
                className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-amber-50 p-1.5 text-xs text-amber-800 dark:bg-amber-900/30 dark:text-amber-200"
              >
                {"\uD83D\uDD27"} {tc.name}: {tc.input}
              </pre>
            ))}
            <div className="mt-1 text-xs text-zinc-400">
              Finish: {rt.response.finishReason}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
