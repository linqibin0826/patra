import "server-only";
import { fetchArticleStats } from "@/lib/analytics/insight";
import { logRequestError } from "@/lib/auth/gateway-response";
import type { Read } from "@/lib/interaction/read";
import type { Page } from "@/lib/interaction/schema";
import { fetchPaperStats, type PaperStats } from "@/lib/interaction/stats";
import { fetchPublicationCards } from "@/lib/portal-api/publications";
import type { Paper } from "@/types/portal";

/// 条目与它的卡片。`paper` 为 null：条目已失效，或 catalog 这次没回它（刚软删、对账还没跑），按已失效外观显示。
/// `stats` 为 null：条目已失效、没有 key，或这一屏的计数取不到（只是不显示计数）。`views` 是看过的人数（v0.18，
/// 行为数据服务）：为 null 时同理；这一屏取到了但这篇没数据是 0
export interface WithPaper<I> {
  item: I;
  paper: Paper | null;
  stats: PaperStats | null;
  views: number | null;
}

/// 给一页条目配卡片、收藏人数与讨论数、看过人数：只为 `ACTIVE` 且有 `key` 的取，三者各一次批量请求、并行发出。卡片
/// 取不到时整页 `unavailable`；计数取不到只是每条 `stats` / `views` 为 null（决策 C，v0.18 简报 §3.11）
export async function attachPapers<I extends { key: string | null; state: string }>(
  page: Page<I>,
): Promise<Read<Page<WithPaper<I>>>> {
  const keyOf = (item: I) => (item.state === "ACTIVE" && item.key ? item.key : null);
  const keys = [...new Set(page.items.flatMap((item) => keyOf(item) ?? []))];
  const [papers, stats, views] = await Promise.all([
    fetchPublicationCards(keys).catch((error: unknown) => {
      logRequestError("/patra-catalog/portal/publications/batch", error);
      return null;
    }),
    fetchPaperStats(keys),
    fetchArticleStats(keys),
  ]);
  if (papers === null) {
    return { kind: "unavailable" };
  }
  const byId = new Map(papers.map((paper) => [paper.id, paper]));
  return {
    kind: "ok",
    data: {
      ...page,
      items: page.items.map((item) => {
        const key = keyOf(item);
        return {
          item,
          paper: key ? (byId.get(key) ?? null) : null,
          stats: key ? (stats?.get(key) ?? null) : null,
          views: key && views ? (views.get(key)?.viewerCount ?? 0) : null,
        };
      }),
    },
  };
}
