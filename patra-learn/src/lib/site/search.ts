// patra-learn/src/lib/site/search.ts
// 站内搜索：在浏览器里查构建时生成的索引（不建服务）。按空白分词，每个词都要出现在标题、摘要、标签名或路线名里，
// 不区分大小写；标题命中的在前，其次按难度。个人环境条目不进结果，只计数（决策 E）
import {
  type ContentCatalog,
  DIFFICULTY_RANK,
  type Difficulty,
  type EntryType,
} from "@/lib/content/types";
import { buildEntryCards } from "./entry-cards";

export interface SearchDoc {
  slug: string;
  title: string;
  summary: string;
  type: EntryType;
  difficulty: Difficulty;
  /// 标签中文名
  tags: string[];
  /// 所在路线名
  routes: string[];
  personalEnv: boolean;
}

export interface SearchFilters {
  type: EntryType | "all";
  /// 空表示不限难度
  difficulties: Difficulty[];
}

export interface SearchResult {
  doc: SearchDoc;
  /// 高亮区间 [起, 止)
  titleMark?: [number, number];
  summaryMark?: [number, number];
}

const NO_FILTERS: SearchFilters = { type: "all", difficulties: [] };

/// 第一个出现在 text 里的词的位置
function firstMark(text: string, terms: string[]): [number, number] | undefined {
  const lower = text.toLowerCase();
  for (const term of terms) {
    const at = lower.indexOf(term);
    if (at >= 0) return [at, at + term.length];
  }
  return undefined;
}

export function searchEntries(docs: SearchDoc[], query: string, filters = NO_FILTERS) {
  const terms = query.toLowerCase().split(/\s+/).filter(Boolean);
  if (terms.length === 0) return { results: [], hits: 0, personalCount: 0 };
  const matched = docs.filter((d) => {
    const haystack = [d.title, d.summary, ...d.tags, ...d.routes].join("\n").toLowerCase();
    return terms.every((t) => haystack.includes(t));
  });
  const visible = matched.filter((d) => !d.personalEnv);
  const inTitle = (d: SearchDoc) => terms.some((t) => d.title.toLowerCase().includes(t));
  const results: SearchResult[] = visible
    .filter((d) => filters.type === "all" || d.type === filters.type)
    .filter((d) => filters.difficulties.length === 0 || filters.difficulties.includes(d.difficulty))
    .sort(
      (a, b) =>
        Number(inTitle(b)) - Number(inTitle(a)) ||
        DIFFICULTY_RANK[a.difficulty] - DIFFICULTY_RANK[b.difficulty] ||
        a.slug.localeCompare(b.slug),
    )
    .map((doc) => {
      const titleMark = firstMark(doc.title, terms);
      const summaryMark = firstMark(doc.summary, terms);
      return { doc, ...(titleMark ? { titleMark } : {}), ...(summaryMark ? { summaryMark } : {}) };
    });
  return { results, hits: visible.length, personalCount: matched.length - visible.length };
}

/// 构建时生成的搜索索引：全部条目（个人环境条目也在，只用于计数）
export function searchDocs(catalog: ContentCatalog): SearchDoc[] {
  return buildEntryCards(catalog).map((c) => ({
    slug: c.slug,
    title: c.title,
    summary: c.summary,
    type: c.type,
    difficulty: c.difficulty,
    tags: c.tagLabels,
    routes: c.routes.map((r) => r.title),
    personalEnv: c.personalEnv,
  }));
}

/// 常搜词里搜得到公开条目的那些
export function availableHints(docs: SearchDoc[], hints: string[]): string[] {
  return hints.filter((h) => searchEntries(docs, h).results.length > 0);
}
