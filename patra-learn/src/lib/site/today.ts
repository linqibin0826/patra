// patra-learn/src/lib/site/today.ts
// 由本地进度算出的展示：「今天读这一条」的候选与理由、掌握度计数、「接着读」。规则见工程 spec
// docs/patra/specs/2026-10-31-learn-pages-design.md 第 3、4 节；now 由调用方传入，测试可固定时间
import type { EntryProgress, Progress } from "@/lib/progress/progress";
import { LEVEL_LABELS } from "./labels";

const DAY = 24 * 60 * 60 * 1000;
/// 标过「吃透了」的，多少天没看才再挑
const MASTERED_REST_DAYS = 30;

/// 最近一次看到：阅读与标记里晚的那个
function lastSeen(record: EntryProgress): number {
  return Math.max(record.readAt ?? 0, record.markedAt ?? 0);
}

/// 0 标过刚接触 / 理解中且至少 1 天没看；1 只打开过且至少 1 天没看，或吃透了且 30 天没看；2 其余
function tier(record: EntryProgress, now: number): number {
  const days = (now - lastSeen(record)) / DAY;
  if (days < 1) return 2;
  if (record.level === 1 || record.level === 2) return 0;
  if (record.level === 3) return days >= MASTERED_REST_DAYS ? 1 : 2;
  return 1;
}

/// 候选池里有记录的条目，按档位、再按最近一次看到从旧到新排；「换一条」沿这个顺序走
export function rankReview<T extends { slug: string }>(pool: T[], progress: Progress, now: number) {
  return pool
    .flatMap((item) => {
      const record = progress[item.slug];
      return record ? [{ item, record }] : [];
    })
    .sort(
      (a, b) =>
        tier(a.record, now) - tier(b.record, now) ||
        lastSeen(a.record) - lastSeen(b.record) ||
        a.item.slug.localeCompare(b.item.slug),
    )
    .map(({ item, record }) => ({ ...item, record }));
}

/// 推这一条的理由
export function reviewReason(record: EntryProgress, now: number): string {
  const days = Math.floor((now - lastSeen(record)) / DAY);
  if (record.level) {
    const label = `你标过「${LEVEL_LABELS[record.level]}」`;
    return days < 1 ? `${label}· 今天看过` : `${label}· ${days} 天没看了`;
  }
  return days < 1 ? "今天看过" : `上次看是 ${days} 天前`;
}

/// 给定条目里各掌握度的篇数；读过 = 标过掌握度
export function masteryCounts(slugs: string[], progress: Progress) {
  const counts = { 1: 0, 2: 0, 3: 0, read: 0 };
  for (const slug of slugs) {
    const level = progress[slug]?.level;
    if (!level) continue;
    counts[level] += 1;
    counts.read += 1;
  }
  return counts;
}

/// 路线里第一篇没标过的；这条路线一篇都没标过、或全标过时为 undefined
export function nextUnread(slugs: string[], progress: Progress): string | undefined {
  if (!slugs.some((s) => progress[s]?.level)) return undefined;
  return slugs.find((s) => !progress[s]?.level);
}

interface RouteLike {
  slug: string;
  title: string;
  entries: { slug: string; title: string }[];
}

/// 首页「接着读」：最近动过的那条路线里的下一篇
export function continueReading(routes: RouteLike[], progress: Progress) {
  const recency = (r: RouteLike) =>
    Math.max(0, ...r.entries.map((e) => (progress[e.slug] ? lastSeen(progress[e.slug] ?? {}) : 0)));
  const ordered = routes.filter((r) => recency(r) > 0).sort((a, b) => recency(b) - recency(a));
  for (const route of ordered) {
    const slug = nextUnread(
      route.entries.map((e) => e.slug),
      progress,
    );
    const index = route.entries.findIndex((e) => e.slug === slug);
    const target = route.entries[index];
    if (target) {
      return {
        route: route.title,
        routeSlug: route.slug,
        position: index + 1,
        slug: target.slug,
        title: target.title,
      };
    }
  }
  return undefined;
}

/// 侧栏路线窗口：当前篇（从 1 数）前后各 radius 篇
export function routeWindow(total: number, position: number, radius = 2) {
  return { start: Math.max(1, position - radius), end: Math.min(total, position + radius) };
}
