// patra-learn/src/lib/content/validate.ts
// 汇总校验：解析 + 单篇规则（正文、公开边界、代码引用、标签）+ 全站规则
import { componentRegistry } from "@/teaching/registry";
import { checkCatalog, checkEntryTags } from "./catalog-rules";
import { checkCodeRefs } from "./code-refs";
import { type CodeRefUse, checkEntryBody } from "./mdx-rules";
import { parseContent, parseEntryFile } from "./parse";
import { checkPublicBoundary } from "./public-boundary";
import type { ContentCatalog, ContentIssue, Entry, RawContent, RawFile, Tag } from "./types";

export interface ValidationDeps {
  /// 路径相对仓库根
  fileExists: (repoPath: string) => boolean;
}

/// 条目文件名（不含目录与扩展名）
function slugOf(file: string): string {
  return (file.split("/").pop() ?? file).replace(/\.mdx$/, "");
}

function byFileThenRule(a: ContentIssue, b: ContentIssue): number {
  return a.file.localeCompare(b.file) || a.rule.localeCompare(b.rule);
}

/// 一篇已解析条目的单篇规则（正文、代码引用、标签）
function checkEntry(
  entry: Entry,
  tags: Tag[],
  deps: ValidationDeps,
): { issues: ContentIssue[]; codeRefs: CodeRefUse[] } {
  const body = checkEntryBody(entry, componentRegistry);
  return {
    issues: [
      ...checkEntryTags(entry, tags),
      ...body.issues,
      ...checkCodeRefs(body.codeRefs, deps.fileExists),
    ],
    codeRefs: body.codeRefs,
  };
}

/// 全站校验：pnpm test 的真实内容测试与体检报告都用它
export function validateContent(
  raw: RawContent,
  deps: ValidationDeps,
): { catalog: ContentCatalog; issues: ContentIssue[]; codeRefs: CodeRefUse[] } {
  const { catalog, issues } = parseContent(raw);
  const codeRefs: CodeRefUse[] = [];
  for (const entry of catalog.entries) {
    const result = checkEntry(entry, catalog.tags, deps);
    issues.push(...result.issues);
    codeRefs.push(...result.codeRefs);
  }
  const files = [...raw.entries, ...raw.routes, ...raw.topics, ...(raw.tags ? [raw.tags] : [])];
  const parsed = new Set(catalog.entries.map((e) => e.slug));
  const unparsed = raw.entries.map((f) => slugOf(f.file)).filter((slug) => !parsed.has(slug));
  issues.push(...files.flatMap(checkPublicBoundary), ...checkCatalog(catalog, { unparsed }));
  return { catalog, issues: issues.sort(byFileThenRule), codeRefs };
}

/// 单篇校验：只跑单篇规则，不查文件名与跨篇规则（给维护技能的模板用）
export function validateEntryFiles(
  files: RawFile[],
  tags: Tag[],
  deps: ValidationDeps,
): ContentIssue[] {
  const issues: ContentIssue[] = [];
  for (const file of files) {
    const parsed = parseEntryFile(file, { requireSlug: false });
    issues.push(...parsed.issues, ...checkPublicBoundary(file));
    if (parsed.entry) issues.push(...checkEntry(parsed.entry, tags, deps).issues);
  }
  return issues.sort(byFileThenRule);
}
