// patra-learn/src/lib/content/indexes.ts
// 构建时索引：路线 / 主题 / 标签 / 版本。个人环境条目进标签与主题索引、不进版本索引（决策 E）；
// 主题下全是个人环境条目时标 personalOnly，页面据此不进主题列表与首页。
// 被取代的条目照常进主题、标签、版本索引，页面据 supersededBy 标「已被取代」
import type { ContentCatalog, Entry } from "./types";

export interface RouteView {
  slug: string;
  title: string;
  summary: string;
  order: number;
  entries: Entry[];
}

export interface TopicView {
  slug: string;
  title: string;
  summary: string;
  entries: Entry[];
  /// 条目全是个人环境：不进主题列表、不上首页
  personalOnly: boolean;
}

export interface TagView {
  slug: string;
  label: string;
  count: number;
  entries: Entry[];
}

export interface VersionView {
  version: string;
  entries: Entry[];
}

/// v0.9 < v0.16：按主、次版本号数字比较
export function compareVersions(a: string, b: string): number {
  const parts = (v: string) => v.slice(1).split(".").map(Number);
  const [aMajor = 0, aMinor = 0] = parts(a);
  const [bMajor = 0, bMinor = 0] = parts(b);
  return aMajor - bMajor || aMinor - bMinor;
}

export function findEntry(catalog: ContentCatalog, slug: string): Entry | undefined {
  return catalog.entries.find((e) => e.slug === slug);
}

/// 按 order 升序；条目按路线顺序解析，不存在的跳过
export function buildRouteIndex(catalog: ContentCatalog): RouteView[] {
  return [...catalog.routes]
    .sort((a, b) => a.order - b.order)
    .map(({ slug, title, summary, order, entries }) => ({
      slug,
      title,
      summary,
      order,
      entries: entries.map((s) => findEntry(catalog, s)).filter((e) => e !== undefined),
    }));
}

/// 条目最早的出处版本
function earliestVersion(entry: Entry): string | undefined {
  return entry.meta.sources.map((s) => s.version).sort(compareVersions)[0];
}

/// 主题按 slug；主题下条目按最早出处版本升序、同版本按 slug
export function buildTopicIndex(catalog: ContentCatalog): TopicView[] {
  const byTime = (a: Entry, b: Entry) =>
    compareVersions(earliestVersion(a) ?? "v0.0", earliestVersion(b) ?? "v0.0") ||
    a.slug.localeCompare(b.slug);
  return [...catalog.topics]
    .sort((a, b) => a.slug.localeCompare(b.slug))
    .map(({ slug, title, summary }) => {
      const entries = catalog.entries.filter((e) => e.meta.topics.includes(slug)).sort(byTime);
      const personalOnly = entries.length > 0 && entries.every((e) => e.meta.personalEnv);
      return { slug, title, summary, entries, personalOnly };
    });
}

/// 标签表顺序；含个人环境条目
export function buildTagIndex(catalog: ContentCatalog): TagView[] {
  return catalog.tags.map(({ slug, label }) => {
    const entries = catalog.entries.filter((e) => e.meta.tags.includes(slug));
    return { slug, label, count: entries.length, entries };
  });
}

/// 版本降序；排除个人环境条目
export function buildVersionIndex(catalog: ContentCatalog): VersionView[] {
  const byVersion = new Map<string, Entry[]>();
  for (const entry of catalog.entries) {
    if (entry.meta.personalEnv) continue;
    for (const version of new Set(entry.meta.sources.map((s) => s.version))) {
      byVersion.set(version, [...(byVersion.get(version) ?? []), entry]);
    }
  }
  return [...byVersion.keys()]
    .sort((a, b) => compareVersions(b, a))
    .map((version) => ({ version, entries: byVersion.get(version) ?? [] }));
}
