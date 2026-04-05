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
          {traffic.map((t, i) => (
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
          ))}
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
            <BodyView body={selected.requestBody} />
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

/** Body sub-view with JSON syntax highlighting */
function BodyView({ body }: { body: string }) {
  const [expanded, setExpanded] = useState(false);
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

  return (
    <div className="nm-body-scroll">
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
