"use client";

import { useState } from "react";
import type { HttpTraffic } from "@/lib/chat-runtime";

interface NetworkPanelProps {
  traffic: HttpTraffic[];
}

type DetailTab = "headers" | "request" | "response";

export function NetworkPanel({ traffic }: NetworkPanelProps) {
  const [selectedIdx, setSelectedIdx] = useState(0);
  const [detailTab, setDetailTab] = useState<DetailTab>("headers");

  if (traffic.length === 0) return null;

  const selected = traffic[selectedIdx];
  const totalDuration = traffic.reduce((s, t) => s + t.durationMs, 0);
  const maxDuration = Math.max(...traffic.map((t) => t.durationMs));
  const totalTokens = traffic.reduce(
    (s, t) => {
      try {
        const body = JSON.parse(t.responseBody);
        const usage = body?.usage;
        return {
          in: s.in + (usage?.input_tokens ?? 0),
          out: s.out + (usage?.output_tokens ?? 0),
        };
      } catch {
        return s;
      }
    },
    { in: 0, out: 0 }
  );

  return (
    <div
      className="nm-root"
      style={{
        fontFamily:
          "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
        fontVariantNumeric: "tabular-nums",
      }}
    >
      {/* Toolbar */}
      <div className="nm-toolbar">
        <div className="nm-toolbar-left">
          <span className="nm-status-dot" />
          <span className="nm-toolbar-title">Network</span>
          <span className="nm-toolbar-sep">│</span>
          <span className="nm-toolbar-stat">
            {traffic.length} requests · {(totalDuration / 1000).toFixed(1)}s ·{" "}
            {(totalTokens.in + totalTokens.out).toLocaleString()} tokens
          </span>
        </div>
      </div>

      {/* Split view */}
      <div className="nm-split">
        {/* Left: Request list */}
        <div className="nm-list">
          <div className="nm-list-header">
            <span></span>
            <span>Endpoint</span>
            <span className="nm-col-right">Time</span>
            <span className="nm-col-right">Status</span>
          </div>
          {traffic.map((t, i) => {
            const pct = maxDuration > 0 ? Math.round((t.durationMs / maxDuration) * 100) : 0;
            const tier =
              t.durationMs < 1000
                ? "bg-emerald-500"
                : t.durationMs < 3000
                  ? "bg-amber-500"
                  : "bg-red-500";
            const reqSize = (t.requestBody.length / 1024).toFixed(1);
            const respSize = (t.responseBody.length / 1024).toFixed(1);
            return (
              <div
                key={i}
                className={`nm-list-item ${i === selectedIdx ? "nm-list-item-active" : ""}`}
                onClick={() => setSelectedIdx(i)}
              >
                <span
                  className={`nm-round-badge ${i === selectedIdx ? "nm-round-badge-active" : ""}`}
                >
                  R{t.round}
                </span>
                <div className="nm-list-item-main">
                  <span className="nm-list-item-name">
                    {t.method} {safePathname(t.url)}
                  </span>
                  {/* Timing bar */}
                  <div className="mt-0.5 h-1 w-full rounded-full bg-zinc-700/50">
                    <div
                      className={`h-full rounded-full ${tier}`}
                      style={{ width: `${Math.max(pct, 3)}%` }}
                    />
                  </div>
                  {/* Payload sizes */}
                  <div className="mt-0.5 flex gap-2 text-[9px] nm-text-muted">
                    <span>req {reqSize}KB</span>
                    <span>resp {respSize}KB</span>
                  </div>
                </div>
                <span className="nm-col-right nm-text-muted">
                  {(t.durationMs / 1000).toFixed(1)}s
                </span>
                <span
                  className={`nm-col-right nm-status-badge ${
                    t.statusCode >= 200 && t.statusCode < 300
                      ? "nm-status-ok"
                      : "nm-status-err"
                  }`}
                >
                  {t.statusCode}
                </span>
              </div>
            );
          })}
        </div>

        {/* Right: Detail panel */}
        <div className="nm-detail">
          {/* Detail tabs */}
          <div className="nm-detail-tabs">
            {(["headers", "request", "response"] as DetailTab[]).map((tab) => (
              <button
                key={tab}
                className={`nm-detail-tab ${detailTab === tab ? "nm-detail-tab-active" : ""}`}
                onClick={() => setDetailTab(tab)}
              >
                {tab === "headers"
                  ? "Headers"
                  : tab === "request"
                    ? "Request Body"
                    : "Response Body"}
              </button>
            ))}
          </div>

          {/* General info (always visible) */}
          <div className="nm-detail-info">
            <div className="nm-info-grid">
              <span className="nm-info-label">Request URL</span>
              <span className="nm-info-value nm-info-url">{selected.url}</span>

              <span className="nm-info-label">Method</span>
              <span>
                <span className="nm-method-badge">{selected.method}</span>
              </span>

              <span className="nm-info-label">Status</span>
              <span>
                <span
                  className={
                    selected.statusCode >= 200 && selected.statusCode < 300
                      ? "nm-status-ok"
                      : "nm-status-err"
                  }
                >
                  {selected.statusCode}
                </span>
                <span className="nm-text-muted">
                  {" "}
                  {selected.statusCode === 200
                    ? "OK"
                    : selected.statusCode <= 0
                      ? "Error"
                      : ""}
                </span>
              </span>

              <span className="nm-info-label">Duration</span>
              <span className="nm-info-value">
                {selected.durationMs.toLocaleString()} ms
              </span>
            </div>
          </div>

          {/* Tab content */}
          {detailTab === "headers" && (
            <HeadersView
              requestHeaders={selected.requestHeaders}
              responseHeaders={selected.responseHeaders}
            />
          )}
          {detailTab === "request" && (
            <BodyView body={selected.requestBody} showTokens />
          )}
          {detailTab === "response" && (
            <BodyView body={selected.responseBody} />
          )}
        </div>
      </div>
    </div>
  );
}

/** Safe URL pathname extraction — avoids constructor errors on invalid URLs */
function safePathname(url: string): string {
  try {
    return new URL(url).pathname;
  } catch {
    return url.slice(0, 50);
  }
}

/** Headers sub-view */
function HeadersView({
  requestHeaders,
  responseHeaders,
}: {
  requestHeaders: { name: string; value: string; sensitive: boolean }[];
  responseHeaders: { name: string; value: string; sensitive: boolean }[];
}) {
  return (
    <div className="nm-headers-scroll">
      <HeaderSection title="Request Headers" headers={requestHeaders} />
      <div className="nm-divider" />
      <HeaderSection title="Response Headers" headers={responseHeaders} />
    </div>
  );
}

function HeaderSection({
  title,
  headers,
}: {
  title: string;
  headers: { name: string; value: string; sensitive: boolean }[];
}) {
  return (
    <div className="nm-header-section">
      <div className="nm-section-label">
        {title} <span className="nm-count">{headers.length}</span>
      </div>
      <div className="nm-header-grid">
        {headers.map((h, i) => (
          <div key={i} className="nm-header-row">
            <span className="nm-header-key">{h.name}</span>
            {h.sensitive ? (
              <span className="nm-header-sensitive">{h.value}</span>
            ) : (
              <span className="nm-header-val">{h.value}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

/** Rough token estimate — ~4 chars per token for English/code, ~2 for CJK */
function estimateTokens(text: string): number {
  if (!text) return 0;
  const cjk = (text.match(/[\u4e00-\u9fff\u3040-\u30ff\uac00-\ud7af]/g) || []).length;
  const rest = text.length - cjk;
  return Math.ceil(cjk / 2 + rest / 4);
}

/** Body sub-view with JSON syntax highlighting, copy button, and token estimate */
function BodyView({ body, showTokens }: { body: string; showTokens?: boolean }) {
  const [expanded, setExpanded] = useState(false);
  const [copied, setCopied] = useState(false);
  const TRUNCATE_SIZE = 10240; // 10KB

  if (!body) {
    return <div className="nm-body-empty">No body</div>;
  }

  const isTruncated = body.length > TRUNCATE_SIZE && !expanded;
  const displayBody = isTruncated ? body.slice(0, TRUNCATE_SIZE) : body;

  // Try to pretty-print JSON
  let formatted: string;
  try {
    formatted = JSON.stringify(JSON.parse(displayBody), null, 2);
  } catch {
    formatted = displayBody;
  }

  const tokenCount = estimateTokens(body);

  const handleCopy = async () => {
    // Copy the pretty-printed version
    let copyText: string;
    try {
      copyText = JSON.stringify(JSON.parse(body), null, 2);
    } catch {
      copyText = body;
    }
    await navigator.clipboard.writeText(copyText);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="nm-body-scroll">
      {/* Toolbar: copy + token estimate */}
      <div className="nm-body-toolbar">
        <button className="nm-copy-btn" onClick={handleCopy}>
          {copied ? "✓ Copied" : "Copy"}
        </button>
        {showTokens && (
          <span className="nm-token-badge">
            ~{tokenCount.toLocaleString()} tokens
          </span>
        )}
        <span className="nm-text-muted" style={{ marginLeft: "auto" }}>
          {(body.length / 1024).toFixed(1)} KB
        </span>
      </div>
      <pre className="nm-json-pre">
        <code
          dangerouslySetInnerHTML={{ __html: highlightJson(formatted) }}
        />
      </pre>
      {isTruncated && (
        <button className="nm-expand-btn" onClick={() => setExpanded(true)}>
          Show full body ({(body.length / 1024).toFixed(1)} KB)
        </button>
      )}
    </div>
  );
}

/** Lightweight JSON syntax highlighting — no dependencies */
function highlightJson(json: string): string {
  return json
    .replace(
      /("(?:\\.|[^"\\])*")\s*:/g,
      '<span class="nm-json-key">$1</span>:'
    )
    .replace(
      /:\s*("(?:\\.|[^"\\])*")/g,
      ': <span class="nm-json-string">$1</span>'
    )
    .replace(/:\s*(\d+(?:\.\d+)?)/g, ': <span class="nm-json-number">$1</span>')
    .replace(
      /:\s*(true|false)/g,
      ': <span class="nm-json-boolean">$1</span>'
    )
    .replace(/:\s*(null)/g, ': <span class="nm-json-null">$1</span>');
}
