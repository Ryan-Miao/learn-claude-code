"use client";

import { useState, useCallback } from "react";
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

/** Finish reason badge — green for stop, amber for tool_use */
function FinishBadge({ reason }: { reason: string }) {
  const isStop =
    reason === "stop" ||
    reason === "end_turn" ||
    reason === "STOP" ||
    reason === "END_TURN";
  const cls = isStop
    ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300"
    : "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300";
  return (
    <span className={`inline-block rounded px-1.5 py-0.5 text-[10px] font-semibold ${cls}`}>
      {reason}
    </span>
  );
}

/** Timing bar — width proportional to durationMs / maxDuration */
function TimingBar({ durationMs, maxDuration }: { durationMs: number; maxDuration: number }) {
  const pct = maxDuration > 0 ? Math.round((durationMs / maxDuration) * 100) : 0;
  const tier =
    durationMs < 1000 ? "bg-emerald-500" : durationMs < 3000 ? "bg-amber-500" : "bg-red-500";
  return (
    <div className="h-1.5 w-full rounded-full bg-zinc-200 dark:bg-zinc-700">
      <div
        className={`h-full rounded-full transition-all ${tier}`}
        style={{ width: `${Math.max(pct, 2)}%` }}
      />
    </div>
  );
}

/** Token usage stacked bar */
function TokenBar({
  inputTokens,
  outputTokens,
}: {
  inputTokens: number;
  outputTokens: number;
}) {
  const total = inputTokens + outputTokens;
  if (total === 0) return null;
  const inPct = Math.round((inputTokens / total) * 100);
  return (
    <div className="flex items-center gap-2 text-[10px]">
      <div className="h-1.5 flex-1 rounded-full bg-zinc-200 dark:bg-zinc-700 overflow-hidden flex">
        <div className="h-full bg-blue-500" style={{ width: `${inPct}%` }} />
        <div className="h-full bg-emerald-500" style={{ width: `${100 - inPct}%` }} />
      </div>
      <span className="text-blue-600 dark:text-blue-400 shrink-0">{inputTokens}</span>
      <span className="text-zinc-400">/</span>
      <span className="text-emerald-600 dark:text-emerald-400 shrink-0">{outputTokens}</span>
    </div>
  );
}

/** Round step indicator — R1 → R2 → R3 */
function RoundStepper({ total, expandedIdx }: { total: number; expandedIdx: number }) {
  return (
    <div className="flex items-center gap-1">
      {Array.from({ length: total }, (_, i) => {
        const isExpanded = i === expandedIdx;
        const cls = isExpanded
          ? "h-2 w-2 rounded-full bg-blue-500 ring-2 ring-blue-300 dark:ring-blue-700"
          : "h-2 w-2 rounded-full bg-zinc-300 dark:bg-zinc-600";
        return (
          <div key={i} className="flex items-center gap-1">
            <div className={cls} title={`Round ${i + 1}`} />
            {i < total - 1 && (
              <div className="h-px w-2 bg-zinc-300 dark:bg-zinc-600" />
            )}
          </div>
        );
      })}
    </div>
  );
}

/** Overview panel with collapsible rounds, timing bars, token bars */
function OverviewPanel({ roundTrips }: { roundTrips: ApiRoundTrip[] }) {
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const toggle = useCallback((round: number) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      next.has(round) ? next.delete(round) : next.add(round);
      return next;
    });
  }, []);

  const totalInput = roundTrips.reduce((s, rt) => s + rt.response.inputTokens, 0);
  const totalOutput = roundTrips.reduce((s, rt) => s + rt.response.outputTokens, 0);
  const totalDuration = roundTrips.reduce((s, rt) => s + rt.response.durationMs, 0);
  const maxDuration = Math.max(...roundTrips.map((rt) => rt.response.durationMs));
  const expandedIdx = [...expanded].pop() ?? -1;

  return (
    <div className="space-y-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-900">
      {/* Summary header */}
      <div className="flex items-center justify-between border-b border-zinc-200 pb-2 dark:border-zinc-700">
        <div className="flex items-center gap-3">
          <span className="text-xs font-medium text-zinc-600 dark:text-zinc-300">
            API Round Trips
          </span>
          <RoundStepper total={roundTrips.length} expandedIdx={expandedIdx} />
          <span className="rounded bg-zinc-200 px-1.5 py-0.5 text-[10px] font-semibold text-zinc-600 dark:bg-zinc-700 dark:text-zinc-300">
            {roundTrips.length} rounds
          </span>
        </div>
        <span className="font-mono text-xs text-zinc-500">
          {totalInput} in / {totalOutput} out / {totalDuration}ms
        </span>
      </div>

      {/* Per-round details — collapsible */}
      {roundTrips.map((rt) => {
        const isOpen = expanded.has(rt.round);
        return (
          <div
            key={rt.round}
            className="rounded border border-zinc-200 bg-white dark:border-zinc-600 dark:bg-zinc-800"
          >
            {/* Collapsible header */}
            <button
              onClick={() => toggle(rt.round)}
              className="flex w-full items-center gap-3 p-2 text-left hover:bg-zinc-50 dark:hover:bg-zinc-750"
            >
              <span className="text-[10px] text-zinc-400">
                {isOpen ? "\u25BC" : "\u25B6"}
              </span>
              <span className="text-xs font-semibold text-zinc-700 dark:text-zinc-200">
                R{rt.round}
              </span>
              <FinishBadge reason={rt.response.finishReason} />
              <div className="flex-1 min-w-0">
                <TimingBar durationMs={rt.response.durationMs} maxDuration={maxDuration} />
              </div>
              <span className="font-mono text-[10px] text-zinc-500 shrink-0">
                {rt.response.durationMs}ms
              </span>
              <span className="font-mono text-[10px] text-zinc-500 shrink-0">
                {rt.request.model}
              </span>
            </button>

            {/* Expanded content */}
            {isOpen && (
              <div className="border-t border-zinc-100 p-2 dark:border-zinc-700">
                {/* Token bar */}
                <div className="mb-2">
                  <TokenBar
                    inputTokens={rt.response.inputTokens}
                    outputTokens={rt.response.outputTokens}
                  />
                </div>

                {/* Request section */}
                <div>
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

                {/* Response section */}
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
                </div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
