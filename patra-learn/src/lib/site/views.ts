// patra-learn/src/lib/site/views.ts
// 内容索引 → 各页面的视图数据（纯函数，构建时调用）。规则见工程 spec
// docs/patra/specs/2026-10-31-learn-pages-design.md 第 5、6 节：个人环境条目不进首页、普通标签与主题列表；
// 路线与前置里引用了还没写的条目时跳过它
import { parseCodeRefAt } from "@/lib/content/code-refs";
import {
  buildRouteIndex,
  buildTopicIndex,
  compareVersions,
  findEntry,
} from "@/lib/content/indexes";
import { readComponents } from "@/lib/content/mdx-rules";
import {
  type ContentCatalog,
  DIFFICULTIES,
  DIFFICULTY_RANK,
  type Difficulty,
  type Source,
} from "@/lib/content/types";
import { buildEntryCards, type EntryCard, type Review, reviewOf } from "./entry-cards";
import { DIFFICULTY_LABELS } from "./labels";

/// 「个人环境」不是标签表里的标签，是 personalEnv 条目的专属标签页
export const PERSONAL_TAG = "personal-env";

export interface EntryLink {
  slug: string;
  title: string;
}

function cardsOf(catalog: ContentCatalog): Map<string, EntryCard> {
  return new Map(buildEntryCards(catalog).map((c) => [c.slug, c]));
}

function card(cards: Map<string, EntryCard>, slug: string): EntryCard {
  const found = cards.get(slug);
  if (!found) throw new Error(`条目 ${slug} 不存在`);
  return found;
}

const byDifficulty = (a: EntryCard, b: EntryCard) =>
  DIFFICULTY_RANK[a.difficulty] - DIFFICULTY_RANK[b.difficulty] || a.slug.localeCompare(b.slug);

/// 新到旧：最新出处版本降序，没出处的排最后，同版本按 slug
const byLatest = (a: EntryCard, b: EntryCard) => {
  if (a.latestVersion && b.latestVersion) {
    return compareVersions(b.latestVersion, a.latestVersion) || a.slug.localeCompare(b.slug);
  }
  return a.latestVersion ? -1 : b.latestVersion ? 1 : a.slug.localeCompare(b.slug);
};

/// 侧栏路线窗口：当前篇（从 1 数）前后各 radius 篇
export function routeWindow(total: number, position: number, radius = 2) {
  return { start: Math.max(1, position - radius), end: Math.min(total, position + radius) };
}

// ---------- 路线 ----------

export interface RouteCard {
  slug: string;
  title: string;
  summary: string;
  count: number;
  from?: Difficulty;
  to?: Difficulty;
  entries: EntryLink[];
}

export function routeCards(catalog: ContentCatalog): RouteCard[] {
  return buildRouteIndex(catalog).map((r) => ({
    slug: r.slug,
    title: r.title,
    summary: r.summary,
    count: r.entries.length,
    ...(r.entries.length > 0
      ? { from: r.entries[0]?.meta.difficulty, to: r.entries.at(-1)?.meta.difficulty }
      : {}),
    entries: r.entries.map((e) => ({ slug: e.slug, title: e.meta.title })),
  }));
}

export interface RouteRow {
  n: number;
  card: EntryCard;
  /// 这一篇也在哪些路线里
  alsoIn: string[];
}

export function routePage(catalog: ContentCatalog, slug: string) {
  const routes = buildRouteIndex(catalog);
  const index = routes.findIndex((r) => r.slug === slug);
  const view = routes[index];
  if (!view) return undefined;
  const cards = cardsOf(catalog);
  const rows: RouteRow[] = view.entries.map((e, i) => {
    const c = card(cards, e.slug);
    return {
      n: i + 1,
      card: c,
      alsoIn: c.routes.filter((r) => r.slug !== slug).map((r) => r.title),
    };
  });
  const levels = Object.fromEntries(
    DIFFICULTIES.map((d) => [d, rows.filter((r) => r.card.difficulty === d).length]),
  ) as Record<Difficulty, number>;
  const others = [...routes.slice(index + 1), ...routes.slice(0, index)].slice(0, 2);
  return {
    slug,
    title: view.title,
    summary: view.summary,
    index: index + 1,
    count: rows.length,
    minutes: rows.reduce((sum, r) => sum + r.card.minutes, 0),
    levels,
    bands: DIFFICULTIES.map((difficulty) => ({
      difficulty,
      rows: rows.filter((r) => r.card.difficulty === difficulty),
    })).filter((b) => b.rows.length > 0),
    next: others.map((r) => ({ slug: r.slug, title: r.title, count: r.entries.length })),
    all: routes.map((r) => ({ slug: r.slug, title: r.title, count: r.entries.length })),
  };
}

// ---------- 主题 ----------

export interface TopicCard {
  slug: string;
  title: string;
  summary: string;
  count: number;
  from?: string;
  to?: string;
  entries: string[];
}

function topicCard(
  view: ReturnType<typeof buildTopicIndex>[number],
  cards: Map<string, EntryCard>,
) {
  const versions = view.entries
    .flatMap((e) => e.meta.sources.map((s) => s.version))
    .sort(compareVersions);
  return {
    slug: view.slug,
    title: view.title,
    summary: view.summary,
    count: view.entries.length,
    ...(versions.length > 0 ? { from: versions[0], to: versions.at(-1) } : {}),
    entries: view.entries.map((e) => card(cards, e.slug).slug),
  };
}

/// 主题列表：不含 personalOnly，按最新出处版本降序（正在长的在前）
export function topicList(catalog: ContentCatalog): TopicCard[] {
  const cards = cardsOf(catalog);
  return buildTopicIndex(catalog)
    .filter((t) => !t.personalOnly && t.entries.length > 0)
    .map((t) => topicCard(t, cards))
    .sort(
      (a, b) => compareVersions(b.to ?? "v0.0", a.to ?? "v0.0") || a.slug.localeCompare(b.slug),
    );
}

export interface TopicItem {
  card: EntryCard;
  /// 取代它的条目标题
  supersededBy?: string;
  /// 它取代了哪些条目（标题）
  replaces: string[];
}

export function topicPage(catalog: ContentCatalog, slug: string) {
  const view = buildTopicIndex(catalog).find((t) => t.slug === slug);
  if (!view) return undefined;
  const cards = cardsOf(catalog);
  const groups: { version: string; issues: string[]; items: TopicItem[] }[] = [];
  for (const e of view.entries) {
    const version = card(cards, e.slug).earliestVersion ?? "v0.0";
    const target = findEntry(catalog, e.meta.supersededBy ?? "");
    const item: TopicItem = {
      card: card(cards, e.slug),
      ...(target ? { supersededBy: target.meta.title } : {}),
      replaces: catalog.entries
        .filter((o) => o.meta.supersededBy === e.slug)
        .map((o) => o.meta.title),
    };
    const issues = e.meta.sources.flatMap((s) => (s.issue ? [s.issue] : []));
    const group = groups.find((g) => g.version === version);
    if (group) {
      group.items.push(item);
      group.issues = [...new Set([...group.issues, ...issues])].sort();
    } else {
      groups.push({ version, issues: [...new Set(issues)].sort(), items: [item] });
    }
  }
  const summary = topicCard(view, cards);
  return {
    ...summary,
    personalOnly: view.personalOnly,
    supersededCount: view.entries.filter((e) => e.meta.supersededBy).length,
    groups,
    others: topicList(catalog).filter((t) => t.slug !== slug),
  };
}

// ---------- 标签 ----------

export function tagList(catalog: ContentCatalog) {
  const publicEntries = catalog.entries.filter((e) => !e.meta.personalEnv);
  return {
    tags: catalog.tags.map(({ slug, label }) => ({
      slug,
      label,
      count: publicEntries.filter((e) => e.meta.tags.includes(slug)).length,
    })),
    personalCount: catalog.entries.length - publicEntries.length,
  };
}

/// 标签页：普通标签不列个人环境条目；PERSONAL_TAG 列全部个人环境条目。按难度从低到高
export function tagPage(catalog: ContentCatalog, slug: string) {
  const cards = buildEntryCards(catalog);
  if (slug === PERSONAL_TAG) {
    const list = cards.filter((c) => c.personalEnv).sort(byDifficulty);
    return { slug, label: "个人环境", personal: true, cards: list };
  }
  const tag = catalog.tags.find((t) => t.slug === slug);
  if (!tag) return undefined;
  const members = new Set(
    catalog.entries.filter((e) => e.meta.tags.includes(slug)).map((e) => e.slug),
  );
  const list = cards.filter((c) => !c.personalEnv && members.has(c.slug)).sort(byDifficulty);
  return { slug, label: tag.label, personal: false, cards: list };
}

// ---------- 条目 ----------

export interface PrerequisiteItem extends EntryLink {
  difficulty: Difficulty;
  /// 难度 · 时长 · 在哪条路线第几篇
  meta: string;
}

export function entryPage(catalog: ContentCatalog, slug: string) {
  const entry = findEntry(catalog, slug);
  if (!entry) return undefined;
  const cards = cardsOf(catalog);
  const self = card(cards, slug);
  const link = (s: string): EntryLink => ({ slug: s, title: card(cards, s).title });

  const [place] = self.routes;
  const routeView = place ? buildRouteIndex(catalog).find((r) => r.slug === place.slug) : undefined;
  const routeEntries = routeView?.entries.map((e) => link(e.slug)) ?? [];
  const mainRoute = place ? { ...place, entries: routeEntries } : undefined;
  const prevSlug = place ? routeEntries[place.position - 2]?.slug : undefined;
  const nextSlug = place ? routeEntries[place.position]?.slug : undefined;

  const direct = entry.meta.prerequisites.filter((s) => cards.has(s));
  const prerequisites: PrerequisiteItem[] = direct.map((s) => {
    const c = card(cards, s);
    const at = c.routes.find((r) => r.slug === place?.slug) ?? c.routes[0];
    const parts = [DIFFICULTY_LABELS[c.difficulty], `${c.minutes} 分钟`];
    if (at) parts.push(`${at.title} 第 ${at.position} 篇`);
    return { slug: s, title: c.title, difficulty: c.difficulty, meta: parts.join(" · ") };
  });
  const indirect = [
    ...new Set(direct.flatMap((s) => findEntry(catalog, s)?.meta.prerequisites ?? [])),
  ]
    .filter((s) => s !== slug && !direct.includes(s) && cards.has(s))
    .map(link);

  const topics = buildTopicIndex(catalog)
    .filter((t) => entry.meta.topics.includes(t.slug))
    .map((t) => ({
      slug: t.slug,
      title: t.title,
      entries: t.entries.map((e) => ({ ...link(e.slug), personalEnv: e.meta.personalEnv })),
    }));

  const followUps = catalog.entries
    .filter((e) => !e.meta.personalEnv && e.meta.prerequisites.includes(slug))
    .map((e) => link(e.slug));

  const commits = [
    ...new Set(
      readComponents(entry.body, "CodeRef").flatMap((props) => {
        const ref = typeof props.at === "string" ? parseCodeRefAt(props.at) : undefined;
        return ref && ref.sha !== "pending" ? [ref.sha.slice(0, 7)] : [];
      }),
    ),
  ];

  const replacement = entry.meta.supersededBy ? cards.get(entry.meta.supersededBy) : undefined;
  const firstTopic = self.topics[0];
  const crumb = entry.meta.personalEnv
    ? { label: "标签 · 个人环境", href: `/tags/${PERSONAL_TAG}` }
    : place
      ? { label: place.title, href: `/routes/${place.slug}` }
      : firstTopic
        ? { label: `主题 · ${firstTopic.title}`, href: `/topics/${firstTopic.slug}` }
        : undefined;

  return {
    card: self,
    crumb,
    mainRoute,
    prev: prevSlug ? link(prevSlug) : undefined,
    next: nextSlug ? link(nextSlug) : undefined,
    prerequisites,
    indirect,
    topics,
    followUps,
    sources: entry.meta.sources as Source[],
    commits,
    supersededBy: replacement
      ? {
          slug: replacement.slug,
          title: replacement.title,
          ...(replacement.earliestVersion ? { version: replacement.earliestVersion } : {}),
        }
      : undefined,
  };
}

// ---------- 首页 ----------

export interface ReviewCandidate {
  slug: string;
  title: string;
  type: EntryCard["type"];
  difficulty: Difficulty;
  where: string;
  review: Review;
}

export function homeView(catalog: ContentCatalog) {
  const cards = buildEntryCards(catalog);
  const bySlug = cardsOf(catalog);
  const routes = routeCards(catalog);
  const publicCards = cards.filter((c) => !c.personalEnv);
  const [first] = routes;
  const reviewPool: ReviewCandidate[] = catalog.entries.flatMap((e) => {
    if (e.meta.personalEnv || e.meta.supersededBy) return [];
    const review = reviewOf(e.body);
    if (!review) return [];
    const c = card(bySlug, e.slug);
    const [place] = c.routes;
    const where = place
      ? `${place.title} · 第 ${place.position} 篇`
      : c.topics[0]
        ? `主题 · ${c.topics[0].title}`
        : "";
    return [
      { slug: c.slug, title: c.title, type: c.type, difficulty: c.difficulty, where, review },
    ];
  });
  return {
    total: publicCards.length,
    routes,
    starter: first
      ? {
          route: { slug: first.slug, title: first.title, count: first.count },
          entries: first.entries.slice(0, 3).map((e) => {
            const c = card(bySlug, e.slug);
            return { slug: c.slug, title: c.title, difficulty: c.difficulty, minutes: c.minutes };
          }),
        }
      : undefined,
    topics: topicList(catalog).slice(0, 3),
    tags: tagList(catalog),
    recent: [...publicCards].sort(byLatest).slice(0, 3),
    reviewPool,
  };
}
