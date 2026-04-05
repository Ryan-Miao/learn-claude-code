package com.claudecode.tool.impl;

import com.claudecode.lsp.LSPDiagnosticRegistry;
import com.claudecode.lsp.LSPServerManager;
import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

/**
 * LSP 工具 —— 对应 claude-code 中通过 LSP 提供的代码智能功能。
 * <p>
 * 暴露 Language Server Protocol 能力给 Agent：
 * <ul>
 *   <li>goto_definition — 跳转到定义</li>
 *   <li>find_references — 查找引用</li>
 *   <li>hover — 获取悬停信息（类型、文档）</li>
 *   <li>diagnostics — 获取文件诊断信息</li>
 *   <li>document_symbols — 获取文件符号列表</li>
 * </ul>
 * <p>
 * 底层使用 Phase 3C 实现的 LSPServerManager 和 LSPClient。
 */
public class LSPTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(LSPTool.class);

    @Override
    public String name() {
        return "LSP";
    }

    @Override
    public String description() {
        return """
            Query Language Server Protocol for code intelligence. Provides:
            - goto_definition: Jump to where a symbol is defined
            - find_references: Find all usages of a symbol
            - hover: Get type info and documentation for a symbol
            - diagnostics: Get errors/warnings for a file
            - document_symbols: List all symbols in a file
            
            Use this when you need to understand code structure, find symbol definitions, \
            or check for compile errors. Requires an LSP server configured for the file type.""";
    }

    @Override
    public String inputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "action": {
                  "type": "string",
                  "enum": ["goto_definition", "find_references", "hover", "diagnostics", "document_symbols"],
                  "description": "The LSP action to perform"
                },
                "file_path": {
                  "type": "string",
                  "description": "Absolute path to the source file"
                },
                "line": {
                  "type": "integer",
                  "description": "1-based line number (required for goto_definition, find_references, hover)"
                },
                "column": {
                  "type": "integer",
                  "description": "1-based column number (required for goto_definition, find_references, hover)"
                }
              },
              "required": ["action", "file_path"]
            }""";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String action = (String) input.get("action");
        String filePath = (String) input.get("file_path");
        Number lineNum = (Number) input.get("line");
        Number colNum = (Number) input.get("column");

        if (action == null || filePath == null) {
            return "Error: 'action' and 'file_path' are required";
        }

        // Get LSP manager from context
        Object managerObj = context.get("LSP_SERVER_MANAGER");
        if (!(managerObj instanceof LSPServerManager manager)) {
            return "Error: LSP server manager not available. No language server configured.";
        }

        Path path = Path.of(filePath);
        int line = lineNum != null ? lineNum.intValue() : 1;
        int column = colNum != null ? colNum.intValue() : 1;
        // Convert to 0-based for LSP protocol
        int lspLine = line - 1;
        int lspCol = column - 1;

        return switch (action) {
            case "goto_definition" -> gotoDefinition(manager, path, lspLine, lspCol);
            case "find_references" -> findReferences(manager, path, lspLine, lspCol);
            case "hover" -> hover(manager, path, lspLine, lspCol);
            case "diagnostics" -> getDiagnostics(context, path);
            case "document_symbols" -> documentSymbols(manager, path);
            default -> "Error: Unknown action: " + action;
        };
    }

    private String gotoDefinition(LSPServerManager manager, Path file, int line, int col) {
        try {
            Map<String, Object> params = textDocumentPositionParams(file, line, col);
            JsonNode result = manager.sendRequest(file.toString(), "textDocument/definition", params);
            if (result == null || result.isNull()) return "No definition found";
            return formatLocations("Definition", result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String findReferences(LSPServerManager manager, Path file, int line, int col) {
        try {
            Map<String, Object> params = textDocumentPositionParams(file, line, col);
            params.put("context", Map.of("includeDeclaration", true));
            JsonNode result = manager.sendRequest(file.toString(), "textDocument/references", params);
            if (result == null || result.isNull()) return "No references found";
            return formatLocations("References", result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String hover(LSPServerManager manager, Path file, int line, int col) {
        try {
            Map<String, Object> params = textDocumentPositionParams(file, line, col);
            JsonNode result = manager.sendRequest(file.toString(), "textDocument/hover", params);
            if (result == null || result.isNull()) return "No hover information available";
            return formatHover(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String getDiagnostics(ToolContext context, Path file) {
        Object regObj = context.get("LSP_DIAGNOSTIC_REGISTRY");
        if (regObj instanceof LSPDiagnosticRegistry registry) {
            var files = registry.checkAndExtract();
            if (files.isEmpty()) {
                return "No diagnostics for " + file.getFileName();
            }
            String formatted = LSPDiagnosticRegistry.formatForContext(files);
            return formatted.isEmpty() ? "No diagnostics" : formatted;
        }
        return "Diagnostic registry not available";
    }

    private String documentSymbols(LSPServerManager manager, Path file) {
        try {
            Map<String, Object> params = Map.of(
                    "textDocument", Map.of("uri", file.toUri().toString()));
            JsonNode result = manager.sendRequest(file.toString(), "textDocument/documentSymbol", params);
            if (result == null || result.isNull()) return "No symbols found";
            return formatSymbols(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ==================== Helpers ====================

    private Map<String, Object> textDocumentPositionParams(Path file, int line, int col) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", Map.of("uri", file.toUri().toString()));
        params.put("position", Map.of("line", line, "character", col));
        return params;
    }

    @SuppressWarnings("unchecked")
    private String formatLocations(String label, JsonNode result) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(":\n");

        if (result.isArray()) {
            if (result.isEmpty()) return "No " + label.toLowerCase() + " found";
            int count = 0;
            for (JsonNode loc : result) {
                String uri = loc.path("uri").asText("");
                String path = uri.replaceFirst("^file:///", "").replaceFirst("^file://", "");
                sb.append("  ").append(path);
                JsonNode range = loc.get("range");
                if (range != null) {
                    JsonNode start = range.get("start");
                    if (start != null) {
                        sb.append(":").append(start.path("line").asInt() + 1);
                        sb.append(":").append(start.path("character").asInt() + 1);
                    }
                }
                sb.append("\n");
                if (++count >= 20) {
                    sb.append("  ... and ").append(result.size() - 20).append(" more\n");
                    break;
                }
            }
        } else if (result.isObject()) {
            String uri = result.path("uri").asText("");
            sb.append("  ").append(uri.replaceFirst("^file:///", "")).append("\n");
        }
        return sb.toString();
    }

    private String formatHover(JsonNode result) {
        if (result.isObject()) {
            JsonNode contents = result.get("contents");
            if (contents == null) return "No information";
            if (contents.isTextual()) return contents.asText();
            if (contents.isObject()) {
                return contents.path("value").asText("No information");
            }
            if (contents.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : contents) {
                    if (item.isTextual()) sb.append(item.asText()).append("\n");
                    else if (item.isObject()) sb.append(item.path("value").asText()).append("\n");
                }
                return sb.toString().trim();
            }
        }
        return result.toString();
    }

    private String formatSymbols(JsonNode result) {
        if (!result.isArray()) return "No symbols";

        StringBuilder sb = new StringBuilder();
        sb.append("Symbols:\n");
        int count = 0;
        for (JsonNode sym : result) {
            String symName = sym.path("name").asText("?");
            int kind = sym.path("kind").asInt(0);
            String kindStr = symbolKindName(kind);
            sb.append(String.format("  %s %s%n", kindStr, symName));

            // Handle children (DocumentSymbol)
            JsonNode children = sym.get("children");
            if (children != null && children.isArray()) {
                for (JsonNode child : children) {
                    String cName = child.path("name").asText("?");
                    int cKind = child.path("kind").asInt(0);
                    sb.append(String.format("    %s %s%n", symbolKindName(cKind), cName));
                }
            }

            if (++count >= 50) {
                sb.append("  ... and more\n");
                break;
            }
        }
        return sb.toString();
    }

    private String symbolKindName(int kind) {
        return switch (kind) {
            case 1 -> "📄 File";
            case 2 -> "📦 Module";
            case 3 -> "🔲 Namespace";
            case 4 -> "📦 Package";
            case 5 -> "🏷 Class";
            case 6 -> "⚡ Method";
            case 7 -> "🔧 Property";
            case 8 -> "📌 Field";
            case 9 -> "🔨 Constructor";
            case 10 -> "📋 Enum";
            case 11 -> "🔗 Interface";
            case 12 -> "λ Function";
            case 13 -> "📎 Variable";
            case 14 -> "📐 Constant";
            case 15 -> "📝 String";
            case 16 -> "🔢 Number";
            case 17 -> "☑ Boolean";
            case 18 -> "📊 Array";
            case 19 -> "📋 Object";
            case 23 -> "📌 Struct";
            case 26 -> "📎 TypeParameter";
            default -> "  Symbol";
        };
    }

    private String noServerMessage(Path file) {
        String ext = file.toString();
        int dot = ext.lastIndexOf('.');
        ext = dot >= 0 ? ext.substring(dot) : "unknown";
        return "No LSP server configured for " + ext + " files. Configure in plugin settings.";
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        String action = (String) input.getOrDefault("action", "query");
        String file = (String) input.getOrDefault("file_path", "");
        String name = Path.of(file).getFileName().toString();
        return "🔍 LSP " + action + " in " + name;
    }
}
