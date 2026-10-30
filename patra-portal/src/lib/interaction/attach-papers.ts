import "server-only";
import { logRequestError } from "@/lib/auth/gateway-response";
import type { Read } from "@/lib/interaction/read";
import type { Page } from "@/lib/interaction/schema";
import { fetchPaperStats, type PaperStats } from "@/lib/interaction/stats";
import { fetchPublicationCards } from "@/lib/portal-api/publications";
import type { Paper } from "@/types/portal";

/// 条目与它的卡片。`paper` 为 null：条目已失效，或 catalog 这次没回它（刚软删、对账还没跑），按已失效外观显示。
/// `stats` 为 null：条目已失效、没有 key，或这一屏的计数取不到（只是不显示计数）
export interface WithPaper<I> {
  item: I;
  paper: Paper | null;
  stats: PaperStats | null;
}

/// 给一页条目配卡片与收藏人数、讨论数：只为 `ACTIVE` 且有 `key` 的取，两者各一次批量请求、并行发出。卡片取不到时
/// 整页 `unavailable`；计数取不到只是每条 `stats: null`（决策 C）
export async function attachPapers<I extends { key: string | null; state: string }>(
  page: Page<I>,
): Promise<Read<Page<WithPaper<I>>>> {
  const keyOf = (item: I) => (item.state === "ACTIVE" && item.key ? item.key : null);
  const keys = [...new Set(page.items.flatMap((item) => keyOf(item) ?? []))];
  const [papers, stats] = await Promise.all([
    fetchPublicationCards(keys).catch((error: unknown) => {
      logRequestError("/patra-catalog/portal/publications/batch", error);
      return null;
    }),
    fetchPaperStats(keys),
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
        };
      }),
    },
  };
}
