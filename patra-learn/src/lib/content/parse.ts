// patra-learn/src/lib/content/parse.ts
// 原文 → catalog：拆 frontmatter、按 schema 解析条目 / 路线 / 主题 / 标签
import { parse as parseYaml } from "yaml";
import type { z } from "zod";
import { entryMetaSchema, routeSchema, SLUG_PATTERN, tagsSchema, topicSchema } from "./schema";
import type {
  ContentCatalog,
  ContentIssue,
  Entry,
  RawContent,
  RawFile,
  Route,
  Tag,
  Topic,
} from "./types";

const TAGS_FILE = "content/tags.yaml";

/// 拆出 frontmatter 的 YAML 与正文；没有 frontmatter 时返回 null
export function splitFrontmatter(text: string): { data: string; body: string } | null {
  const normalized = text.replaceAll("\r\n", "\n");
  const match = normalized.match(/^---\n([\s\S]*?)\n---(?:\n|$)/);
  if (!match) return null;
  return { data: match[1] ?? "", body: normalized.slice(match[0].length) };
}

/// 文件名（去掉目录与扩展名）
function baseName(file: string): string {
  return (file.split("/").pop() ?? file).replace(/\.[^.]+$/, "");
}

function issue(rule: string, file: string, message: string): ContentIssue {
  return { rule, file, message };
}

/// 把 zod 错误压成一行中文说明
function describeZodError(error: z.ZodError): string {
  return error.issues
    .map((i) => `${i.path.length > 0 ? i.path.join(".") : "（根）"}：${i.message}`)
    .join("；");
}

/// 解析 YAML 文本并按 schema 校验；失败时返回带 rule 的问题
function parseYamlWith<T>(
  schema: z.ZodType<T>,
  text: string,
  file: string,
  rule: string,
): { value?: T; issue?: ContentIssue } {
  let data: unknown;
  try {
    data = parseYaml(text);
  } catch (error) {
    return { issue: issue(rule, file, `YAML 解析失败：${(error as Error).message}`) };
  }
  const result = schema.safeParse(data);
  if (!result.success) return { issue: issue(rule, file, describeZodError(result.error)) };
  return { value: result.data };
}

/// 文件名不是合法 slug 时返回问题
function checkSlug(file: string): ContentIssue | undefined {
  const slug = baseName(file);
  if (SLUG_PATTERN.test(slug)) return undefined;
  return issue("slug-invalid", file, `文件名 ${slug} 不是合法 slug（小写字母、数字、连字符）`);
}

/// 解析一篇条目；frontmatter 不合法时不返回 entry。requireSlug 为 false 时不查文件名（技能模板用）
export function parseEntryFile(
  raw: RawFile,
  options: { requireSlug: boolean } = { requireSlug: true },
): { entry?: Entry; issues: ContentIssue[] } {
  const slugIssue = options.requireSlug ? checkSlug(raw.file) : undefined;
  const parts = splitFrontmatter(raw.text);
  const parsed = parts
    ? parseYamlWith(entryMetaSchema, parts.data, raw.file, "frontmatter-invalid")
    : { issue: issue("frontmatter-invalid", raw.file, "文件开头没有 --- 包起来的 frontmatter") };
  const issues = [slugIssue, parsed.issue].filter((i) => i !== undefined);
  const { value } = parsed;
  if (issues.length > 0 || !parts || !value) return { issues };
  return {
    entry: { slug: baseName(raw.file), file: raw.file, meta: value, body: parts.body },
    issues: [],
  };
}

/// 解析一组以文件名为 slug 的 YAML 文件（路线 / 主题）
function parseNamedYaml<T, R>(
  files: RawFile[],
  schema: z.ZodType<T>,
  build: (slug: string, file: string, value: T) => R,
  issues: ContentIssue[],
): R[] {
  const out: R[] = [];
  for (const raw of files) {
    const slugIssue = checkSlug(raw.file);
    if (slugIssue) {
      issues.push(slugIssue);
      continue;
    }
    const { value, issue: yamlIssue } = parseYamlWith(schema, raw.text, raw.file, "yaml-invalid");
    if (yamlIssue || value === undefined) {
      if (yamlIssue) issues.push(yamlIssue);
      continue;
    }
    out.push(build(baseName(raw.file), raw.file, value));
  }
  return out;
}

/// 解析全部内容；解析失败的文件不进 catalog，问题放进 issues
export function parseContent(raw: RawContent): {
  catalog: ContentCatalog;
  issues: ContentIssue[];
} {
  const issues: ContentIssue[] = [];
  const entries: Entry[] = [];
  for (const file of raw.entries) {
    const result = parseEntryFile(file);
    issues.push(...result.issues);
    if (result.entry) entries.push(result.entry);
  }
  const routes = parseNamedYaml<z.infer<typeof routeSchema>, Route>(
    raw.routes,
    routeSchema,
    (slug, file, v) => ({ slug, file, ...v }),
    issues,
  );
  const topics = parseNamedYaml<z.infer<typeof topicSchema>, Topic>(
    raw.topics,
    topicSchema,
    (slug, file, v) => ({ slug, file, ...v }),
    issues,
  );
  let tags: Tag[] = [];
  if (!raw.tags) {
    issues.push(issue("yaml-invalid", TAGS_FILE, "缺少标签表 content/tags.yaml"));
  } else {
    const { value, issue: tagIssue } = parseYamlWith(
      tagsSchema,
      raw.tags.text,
      raw.tags.file,
      "yaml-invalid",
    );
    if (tagIssue) issues.push(tagIssue);
    tags = value ?? [];
  }
  return { catalog: { entries, routes, topics, tags }, issues };
}
