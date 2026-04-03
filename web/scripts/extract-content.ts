import * as fs from "fs";
import * as path from "path";
import type {
  AgentVersion,
  VersionDiff,
  DocContent,
  VersionIndex,
} from "../src/types/agent-data";
import { VERSION_META, VERSION_ORDER, LEARNING_PATH } from "../src/lib/constants";

// Resolve paths relative to this script's location (web/scripts/)
const WEB_DIR = path.resolve(__dirname, "..");
const REPO_ROOT = path.resolve(WEB_DIR, "..");
const JAVA_SRC_DIR = path.join(
  REPO_ROOT,
  "claude-learn",
  "src",
  "main",
  "java",
  "com",
  "demo",
  "learn"
);
const DOCS_DIR = path.join(REPO_ROOT, "docs");
const OUT_DIR = path.join(WEB_DIR, "src", "data", "generated");

// Map Java filenames to version IDs
// S01AgentLoop.java -> s01
// S02ToolUse.java -> s02
// S12WorktreeIsolation.java -> s12
function filenameToVersionId(filename: string): string | null {
  const base = path.basename(filename, ".java");
  const match = base.match(/^(S\d{2})/);
  if (!match) return null;
  return match[1].replace(/^S(\d{2})/, (m, n) => `s${n}`);
}

// Find the main Java source file for a version (e.g., S01AgentLoop.java in s01/ subdirectory)
function findMainSourceFile(
  versionId: string
): { filePath: string; filename: string } | null {
  const subDir = versionId.toLowerCase();
  const dir = path.join(JAVA_SRC_DIR, subDir);
  if (!fs.existsSync(dir)) return null;

  const files = fs.readdirSync(dir).filter((f) => f.endsWith(".java"));
  // Prefer the file that starts with S{NN} (the main agent file)
  const mainFile = files.find((f) => {
    const base = path.basename(f, ".java");
    return base.toLowerCase().startsWith(subDir.replace("s0", "s") + "agent") ||
      base.toLowerCase().startsWith(subDir) ||
      base.match(new RegExp(`^S${versionId.slice(1)}\\w+`, "i"));
  });

  if (mainFile) {
    return { filePath: path.join(dir, mainFile), filename: mainFile };
  }

  // Fallback: first .java file
  if (files.length > 0) {
    return { filePath: path.join(dir, files[0]), filename: files[0] };
  }

  return null;
}

// Extract classes from Java source
function extractClasses(
  lines: string[]
): { name: string; startLine: number; endLine: number }[] {
  const classes: { name: string; startLine: number; endLine: number }[] = [];
  const classPattern =
    /^(?:public\s+|private\s+|protected\s+)?(?:abstract\s+|static\s+|final\s+)?class\s+(\w+)/;

  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(classPattern);
    if (m) {
      const name = m[1];
      const startLine = i + 1;
      // Find end of class: matching closing brace
      let braceCount = 0;
      let endLine = lines.length;
      let started = false;
      for (let j = i; j < lines.length; j++) {
        for (const ch of lines[j]) {
          if (ch === "{") {
            braceCount++;
            started = true;
          } else if (ch === "}") {
            braceCount--;
          }
        }
        if (started && braceCount === 0) {
          endLine = j + 1;
          break;
        }
      }
      classes.push({ name, startLine, endLine });
    }
  }
  return classes;
}

// Extract methods from Java source
function extractFunctions(
  lines: string[]
): { name: string; signature: string; startLine: number }[] {
  const functions: { name: string; signature: string; startLine: number }[] = [];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    // Match method declarations: visibility? modifiers? returnType methodName(params)
    const methodMatch = line.match(
      /^(?:public|private|protected)?\s*(?:static\s+)?(?:\w+(?:<[^>]+>)?)\s+(\w+)\s*\(([^)]*)\)/
    );
    if (methodMatch && !line.startsWith("class ") && !line.includes("new ") && !line.includes("return")) {
      const name = methodMatch[1];
      // Skip common non-method patterns
      if (["if", "while", "for", "switch", "catch"].includes(name)) continue;
      const signature = line.replace(/\s+/g, " ").trim();
      functions.push({
        name,
        signature,
        startLine: i + 1,
      });
    }
  }
  return functions;
}

// Extract tool names from Java source
// Looks for @Tool annotated methods or "name": "tool_name" patterns
function extractTools(source: string): string[] {
  const tools = new Set<string>();

  // Match @Tool annotation patterns (Spring AI style)
  const toolAnnotationPattern = /@Tool\([^)]*name\s*=\s*"(\w+)"/g;
  let m;
  while ((m = toolAnnotationPattern.exec(source)) !== null) {
    tools.add(m[1]);
  }

  // Also match method names following @Tool annotation (without explicit name)
  const lines = source.split("\n");
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].trim() === "@Tool") {
      // Next non-empty line should have the method
      for (let j = i + 1; j < lines.length; j++) {
        const nextLine = lines[j].trim();
        if (nextLine && !nextLine.startsWith("@")) {
          const methodMatch = nextLine.match(/(\w+)\s*\(/);
          if (methodMatch) {
            tools.add(methodMatch[1]);
          }
          break;
        }
      }
    }
  }

  // Also match string patterns like "name": "tool_name"
  const dictPattern = /"name"\s*:\s*"(\w+)"/g;
  while ((m = dictPattern.exec(source)) !== null) {
    tools.add(m[1]);
  }

  return Array.from(tools);
}

// Count non-blank, non-comment lines (Java style)
function countLoc(lines: string[]): number {
  let inBlockComment = false;
  return lines.filter((line) => {
    const trimmed = line.trim();
    if (inBlockComment) {
      if (trimmed.includes("*/")) {
        inBlockComment = false;
      }
      return false;
    }
    if (trimmed.startsWith("/*")) {
      if (!trimmed.includes("*/") || trimmed.endsWith("/*")) {
        inBlockComment = true;
      }
      return false;
    }
    return trimmed !== "" && !trimmed.startsWith("//") && !trimmed.startsWith("*");
  }).length;
}

// Detect locale from subdirectory path
function detectLocale(relPath: string): "en" | "zh" | "ja" {
  if (relPath.startsWith("zh/") || relPath.startsWith("zh\\")) return "zh";
  if (relPath.startsWith("ja/") || relPath.startsWith("ja\\")) return "ja";
  return "en";
}

// Extract version from doc filename (e.g., "s01-the-agent-loop.md" -> "s01")
function extractDocVersion(filename: string): string | null {
  const m = filename.match(/^(s\d+[a-c]?)-/);
  return m ? m[1] : null;
}

// Main extraction
function main() {
  console.log("Extracting content from Java sources and docs...");
  console.log(`  Repo root: ${REPO_ROOT}`);
  console.log(`  Java src dir: ${JAVA_SRC_DIR}`);
  console.log(`  Docs dir: ${DOCS_DIR}`);

  // Skip extraction if source directories don't exist (e.g. Vercel build).
  if (!fs.existsSync(JAVA_SRC_DIR)) {
    console.log("  Java source directory not found, skipping extraction.");
    console.log("  Using pre-committed generated data.");
    return;
  }

  // 1. Read all version subdirectories
  const subDirs = fs
    .readdirSync(JAVA_SRC_DIR)
    .filter((d) => {
      const fullPath = path.join(JAVA_SRC_DIR, d);
      return fs.statSync(fullPath).isDirectory() && d.match(/^s\d+$/);
    });

  console.log(`  Found ${subDirs.length} version directories`);

  const versions: AgentVersion[] = [];

  for (const subDir of subDirs) {
    const versionId = subDir.replace(/^s0?/, "s").replace(/^s(\d)$/, "s0$1");
    // Normalize: s1 -> s01, s12 -> s12
    const normalizedId = subDir.match(/^s(\d+)$/)
      ? `s${subDir.slice(1).padStart(2, "0")}`
      : subDir;

    const mainFile = findMainSourceFile(normalizedId);
    if (!mainFile) {
      console.warn(`  Skipping ${subDir}: no main source file found`);
      continue;
    }

    const source = fs.readFileSync(mainFile.filePath, "utf-8");
    const lines = source.split("\n");

    const meta = VERSION_META[normalizedId];
    const classes = extractClasses(lines);
    const functions = extractFunctions(lines);
    const tools = extractTools(source);
    const loc = countLoc(lines);

    // Use relative path from claude-learn/ as filename for display
    const displayFilename = mainFile.filename;

    versions.push({
      id: normalizedId,
      filename: displayFilename,
      title: meta?.title ?? normalizedId,
      subtitle: meta?.subtitle ?? "",
      loc,
      tools,
      newTools: [],
      coreAddition: meta?.coreAddition ?? "",
      keyInsight: meta?.keyInsight ?? "",
      classes,
      functions,
      layer: meta?.layer ?? "tools",
      source,
    });
  }

  // Sort versions according to VERSION_ORDER
  const orderMap = new Map(VERSION_ORDER.map((v, i) => [v, i]));
  versions.sort(
    (a, b) => (orderMap.get(a.id as any) ?? 99) - (orderMap.get(b.id as any) ?? 99)
  );

  // 2. Compute newTools for each version
  for (let i = 0; i < versions.length; i++) {
    const prev = i > 0 ? new Set(versions[i - 1].tools) : new Set<string>();
    versions[i].newTools = versions[i].tools.filter((t) => !prev.has(t));
  }

  // 3. Compute diffs between adjacent versions in LEARNING_PATH
  const diffs: VersionDiff[] = [];
  const versionMap = new Map(versions.map((v) => [v.id, v]));

  for (let i = 1; i < LEARNING_PATH.length; i++) {
    const fromId = LEARNING_PATH[i - 1];
    const toId = LEARNING_PATH[i];
    const fromVer = versionMap.get(fromId);
    const toVer = versionMap.get(toId);

    if (!fromVer || !toVer) continue;

    const fromClassNames = new Set(fromVer.classes.map((c) => c.name));
    const fromFuncNames = new Set(fromVer.functions.map((f) => f.name));
    const fromToolNames = new Set(fromVer.tools);

    diffs.push({
      from: fromId,
      to: toId,
      newClasses: toVer.classes
        .map((c) => c.name)
        .filter((n) => !fromClassNames.has(n)),
      newFunctions: toVer.functions
        .map((f) => f.name)
        .filter((n) => !fromFuncNames.has(n)),
      newTools: toVer.tools.filter((t) => !fromToolNames.has(t)),
      locDelta: toVer.loc - fromVer.loc,
    });
  }

  // 4. Read doc files from locale subdirectories (en/, zh/, ja/)
  const docs: DocContent[] = [];

  if (fs.existsSync(DOCS_DIR)) {
    const localeDirs = ["en", "zh", "ja"];
    let totalDocFiles = 0;

    for (const locale of localeDirs) {
      const localeDir = path.join(DOCS_DIR, locale);
      if (!fs.existsSync(localeDir)) continue;

      const docFiles = fs
        .readdirSync(localeDir)
        .filter((f) => f.endsWith(".md"));

      totalDocFiles += docFiles.length;

      for (const filename of docFiles) {
        const version = extractDocVersion(filename);
        if (!version) {
          console.warn(`  Skipping doc ${locale}/${filename}: could not determine version`);
          continue;
        }

        const filePath = path.join(localeDir, filename);
        const content = fs.readFileSync(filePath, "utf-8");

        const titleMatch = content.match(/^#\s+(.+)$/m);
        const title = titleMatch ? titleMatch[1] : filename;

        docs.push({ version, locale: locale as "en" | "zh" | "ja", title, content });
      }
    }

    console.log(`  Found ${totalDocFiles} doc files across ${localeDirs.length} locales`);
  } else {
    console.warn(`  Docs directory not found: ${DOCS_DIR}`);
  }

  // 5. Write output
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const index: VersionIndex = { versions, diffs };
  const indexPath = path.join(OUT_DIR, "versions.json");
  fs.writeFileSync(indexPath, JSON.stringify(index, null, 2));
  console.log(`  Wrote ${indexPath}`);

  const docsPath = path.join(OUT_DIR, "docs.json");
  fs.writeFileSync(docsPath, JSON.stringify(docs, null, 2));
  console.log(`  Wrote ${docsPath}`);

  // Summary
  console.log("\nExtraction complete:");
  console.log(`  ${versions.length} versions`);
  console.log(`  ${diffs.length} diffs`);
  console.log(`  ${docs.length} docs`);
  for (const v of versions) {
    console.log(
      `    ${v.id}: ${v.loc} LOC, ${v.tools.length} tools, ${v.classes.length} classes, ${v.functions.length} functions`
    );
  }
}

main();
