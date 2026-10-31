// patra-learn/src/lib/site/entry-cards.ts
// 条目的列表卡片数据：首页、路线、主题、标签、搜索共用。构建时从内容算出，交给客户端组件的都是可序列化的纯数据

import { buildRouteIndex, compareVersions } from "@/lib/content/indexes";
import { readComponents } from "@/lib/content/mdx-rules";
import type { ContentCatalog, Difficulty, EntryType } from "@/lib/content/types";

export interface RoutePlace {
  slug: string;
  title: string;
  /// 在这条路线里是第几篇（从 1 数，跳过还没写的条目）
  position: number;
  total: number;
}

export interface Review {
  question: string;
  /// 正确选项的文字
  answer: string;
  explain: string;
}

export interface EntryCard {
  slug: string;
  title: string;
  summary: string;
  type: EntryType;
  difficulty: Difficulty;
  tagLabels: string[];
  minutes: number;
  personalEnv: boolean;
  superseded: boolean;
  routes: RoutePlace[];
  topics: { slug: string; title: string }[];
  /// 出处里最新的版本
  latestVersion?: string;
  /// 出处里最早的版本
  earliestVersion?: string;
}

const CJK = /[㐀-鿿豈-﫿]/g;
const WORD = /[A-Za-z0-9_]+/g;

/// 汉字每分钟 400 个、西文每分钟 200 词，向上取整，至少 1 分钟
export function readingMinutes(body: string): number {
  const cjk = body.match(CJK)?.length ?? 0;
  const words = body.match(WORD)?.length ?? 0;
  return Math.max(1, Math.ceil(cjk / 400 + words / 200));
}

const LETTERS = ["A", "B", "C", "D"];

/// 开头诊断题当回顾题：题干、正确选项、讲解（去掉开头的「答案是 X。」，正确选项已单独写出）
export function reviewOf(body: string): Review | undefined {
  const props = readComponents(body, "Diagnose")[0];
  if (!props) return undefined;
  const { question, options, answer, ifWrong } = props;
  if (typeof question !== "string" || typeof ifWrong !== "string") return undefined;
  if (!Array.isArray(options) || typeof answer !== "string") return undefined;
  const correct = options[LETTERS.indexOf(answer)];
  if (typeof correct !== "string") return undefined;
  const explain = ifWrong.replace(/^答案是\s*[A-D]\s*[。：:，,]\s*/, "");
  return { question, answer: correct, explain };
}

const cache = new WeakMap<ContentCatalog, EntryCard[]>();

/// 全部条目的卡片，顺序同 catalog.entries；同一份内容只算一次
export function buildEntryCards(catalog: ContentCatalog): EntryCard[] {
  const cached = cache.get(catalog);
  if (cached) return cached;
  const places = new Map<string, RoutePlace[]>();
  for (const view of buildRouteIndex(catalog)) {
    view.entries.forEach((e, i) => {
      const place = {
        slug: view.slug,
        title: view.title,
        position: i + 1,
        total: view.entries.length,
      };
      places.set(e.slug, [...(places.get(e.slug) ?? []), place]);
    });
  }
  const tagLabel = new Map(catalog.tags.map((t) => [t.slug, t.label]));
  const topicTitle = new Map(catalog.topics.map((t) => [t.slug, t.title]));
  const cards = catalog.entries.map(({ slug, meta, body }) => {
    const versions = meta.sources.map((s) => s.version).sort(compareVersions);
    return {
      slug,
      title: meta.title,
      summary: meta.summary,
      type: meta.type,
      difficulty: meta.difficulty,
      tagLabels: meta.tags.map((t) => tagLabel.get(t) ?? t),
      minutes: readingMinutes(body),
      personalEnv: meta.personalEnv,
      superseded: meta.supersededBy !== undefined,
      routes: places.get(slug) ?? [],
      topics: meta.topics.map((t) => ({ slug: t, title: topicTitle.get(t) ?? t })),
      ...(versions.length > 0
        ? { earliestVersion: versions[0], latestVersion: versions.at(-1) }
        : {}),
    };
  });
  cache.set(catalog, cards);
  return cards;
}
