// patra-learn/src/lib/annotations/annotations.ts
// 读者批注：读条目时选中文字记下的「有问题」与「笔记」，只存在读者浏览器的 localStorage 里（同进度）。
// 纯函数：解析容错、增删改返回新列表；「有问题」可以整理成一段 Markdown 交给 Claude 改条目

import type { Quote } from "./anchor";

export const ANNOTATIONS_KEY = "patra-learn.annotations";

/// question 有问题（讲错了或没看懂，要交给 Claude 改）、note 笔记（留给自己）
export type AnnotationKind = "question" | "note";

export interface Annotation {
  id: string;
  /// 所在条目
  slug: string;
  kind: AnnotationKind;
  /// 读者写的说明，可以为空
  text: string;
  quote: Quote;
  /// 毫秒时间戳
  createdAt: number;
  updatedAt?: number;
  /// 导出给 Claude 的时间
  exportedAt?: number;
  /// 上次打开条目时没找到原文（条目改版了）
  orphaned?: boolean;
}

export type Annotations = readonly Annotation[];

/// localStorage 用到的那几个方法，测试里换成假的
export interface AnnotationStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

function isText(value: unknown): value is string {
  return typeof value === "string";
}

function isTime(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0;
}

function cleanQuote(value: unknown): Quote | undefined {
  if (typeof value !== "object" || value === null) return undefined;
  const { exact, prefix, suffix } = value as Record<string, unknown>;
  if (!isText(exact) || exact.length === 0 || !isText(prefix) || !isText(suffix)) return undefined;
  return { exact, prefix, suffix };
}

/// 一条记录缺字段或类型不对时返回 undefined
function cleanAnnotation(value: unknown): Annotation | undefined {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return undefined;
  const { id, slug, kind, text, quote, createdAt, updatedAt, exportedAt, orphaned } =
    value as Record<string, unknown>;
  const cleanedQuote = cleanQuote(quote);
  if (!isText(id) || !id || !isText(slug) || !slug || !isText(text) || !isTime(createdAt)) {
    return undefined;
  }
  if ((kind !== "question" && kind !== "note") || !cleanedQuote) return undefined;
  return {
    id,
    slug,
    kind,
    text,
    quote: cleanedQuote,
    createdAt,
    ...(isTime(updatedAt) ? { updatedAt } : {}),
    ...(isTime(exportedAt) ? { exportedAt } : {}),
    ...(orphaned === true ? { orphaned } : {}),
  };
}

/// 存储原文 → 批注列表；不是 JSON 数组时是空列表，坏的那一条丢掉
export function parseAnnotations(raw: string | null): Annotations {
  if (!raw) return [];
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    return [];
  }
  if (!Array.isArray(data)) return [];
  return data.map(cleanAnnotation).filter((a): a is Annotation => a !== undefined);
}

export function withAdded(list: Annotations, annotation: Annotation): Annotations {
  return [...list, annotation];
}

export function withEdited(
  list: Annotations,
  id: string,
  change: { kind: AnnotationKind; text: string },
  now: number,
): Annotations {
  return list.map((a) => (a.id === id ? { ...a, ...change, updatedAt: now } : a));
}

export function withRemoved(list: Annotations, id: string): Annotations {
  return list.filter((a) => a.id !== id);
}

/// 条目页挂完一遍：这一篇里 found 之外的标成原文已变，found 里的清掉标记；没变化时返回原列表
export function withAnchorResult(
  list: Annotations,
  slug: string,
  found: ReadonlySet<string>,
): Annotations {
  let changed = false;
  const next = list.map((a): Annotation => {
    if (a.slug !== slug) return a;
    const orphaned = !found.has(a.id);
    if (orphaned === (a.orphaned === true)) return a;
    changed = true;
    if (orphaned) return { ...a, orphaned: true };
    const { orphaned: _orphaned, ...rest } = a;
    return rest;
  });
  return changed ? next : list;
}

/// 可以导出的：原文还在的「有问题」，默认只要没导出过的
export function exportable(list: Annotations, includeExported: boolean): Annotations {
  return list.filter(
    (a) => a.kind === "question" && !a.orphaned && (includeExported || a.exportedAt === undefined),
  );
}

export function withExported(list: Annotations, ids: readonly string[], now: number): Annotations {
  const picked = new Set(ids);
  return list.map((a) => (picked.has(a.id) ? { ...a, exportedAt: now } : a));
}

function monthDay(date: Date) {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(date.getMonth() + 1)}/${pad(date.getDate())}`;
}

/// 整理成一段贴给 Claude 的 Markdown；titles 是 slug → 条目标题，条目不在了用 slug
export function exportMarkdown(
  items: Annotations,
  titles: Readonly<Record<string, string>>,
  origin: string,
  now: Date,
): string {
  const lines = [`# 学习站批注：有问题 ${items.length} 条（${monthDay(now)} 导出）`];
  items.forEach((a, i) => {
    lines.push(
      "",
      `## ${i + 1}. ${titles[a.slug] ?? a.slug}`,
      `页面：${origin}/entries/${a.slug}`,
      `文件：patra-learn/content/entries/${a.slug}.mdx`,
      `原文：> ${a.quote.exact}`,
      `我的问题：${a.text || "（没写说明）"}`,
    );
  });
  return lines.join("\n");
}

export function loadAnnotations(storage: AnnotationStorage | undefined): Annotations {
  try {
    return parseAnnotations(storage?.getItem(ANNOTATIONS_KEY) ?? null);
  } catch {
    return [];
  }
}

/// 写成功返回 true；存储不可用或写失败返回 false，由调用方决定退到内存
export function saveAnnotations(
  storage: AnnotationStorage | undefined,
  list: Annotations,
): boolean {
  if (!storage) return false;
  try {
    storage.setItem(ANNOTATIONS_KEY, JSON.stringify(list));
    return true;
  } catch {
    return false;
  }
}
