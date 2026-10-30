"use client";

import { notifyManager, type QueryCache, useQueryClient } from "@tanstack/react-query";
import { useCallback, useSyncExternalStore } from "react";
import { useCollectDelta } from "@/components/library/LibraryStatusProvider";
import { FactCell } from "@/components/portal/paper-detail/FactCell";
import type { ThreadsData } from "@/lib/discussion/cache";
import { formatCount } from "@/lib/format-count";
import { queryKeys } from "@/lib/query-keys";

/// 事实条「收藏」格（简报 §3.7）：公开的收藏人数，本人在页头收藏 / 取消时 ±1；0 不出现
export function PaperSavedFact({
  paperKey,
  collectorCount,
}: {
  paperKey: string;
  collectorCount: number;
}) {
  const count = Math.max(0, collectorCount + useCollectDelta(paperKey));
  return count > 0 ? <FactCell term="收藏">{formatCount(count)} 人</FactCell> : null;
}

/// 事实条「讨论」格（简报 §3.7、§4.3）：跟着讨论区的数据走——讨论区列表已在缓存里时取它第一页的条数（本人发表 /
/// 删除后已 ±1），否则用页面请求时取到的统计；0 不出现
export function PaperDiscussionFact({
  paperKey,
  discussionCount,
}: {
  paperKey: string;
  discussionCount: number;
}) {
  const cached = useCachedDiscussionTotal(paperKey);
  const count = cached ?? discussionCount;
  if (count <= 0) {
    return null;
  }
  return (
    <FactCell term="讨论">
      <a href="#discussion" className="text-link transition-colors hover:text-link-hover">
        {formatCount(count)} 条
      </a>
    </FactCell>
  );
}

/// 这篇文献讨论区列表（任一排序）缓存里第一页的 `total`；几种排序都在缓存里时取最近更新的那份（本人发表 / 删除
/// 只改当前排序的缓存）。没有缓存为 undefined
function useCachedDiscussionTotal(paperKey: string): number | undefined {
  const cache = useQueryClient().getQueryCache();
  // 讨论区在渲染期间把首屏写进缓存，缓存同步通知订阅者：照 useQuery 的做法交给 notifyManager 排到渲染之后
  const subscribe = useCallback(
    (onChange: () => void) => cache.subscribe(notifyManager.batchCalls(onChange)),
    [cache],
  );
  const snapshot = () => latestTotal(cache, paperKey);
  // 服务端渲染与水合都用页面请求时的统计（两块各自流式到达，水合时缓存里可能已有讨论区的数据），挂载后再跟缓存
  return useSyncExternalStore(subscribe, snapshot, serverSnapshot);
}

/// 服务端与水合时的快照：没有讨论区缓存
function serverSnapshot(): undefined {
  return undefined;
}

/// 缓存里这篇文献讨论区列表的第一页条数：几份排序里取最近一次写入的
function latestTotal(cache: QueryCache, paperKey: string): number | undefined {
  let latest: { updatedAt: number; total: number | undefined } | undefined;
  for (const query of cache.findAll({
    queryKey: queryKeys.discussionSubject("publication", paperKey),
  })) {
    const data = query.state.data as ThreadsData | undefined;
    if (query.queryKey[3] !== "threads" || data === undefined) {
      continue;
    }
    if (latest === undefined || query.state.dataUpdatedAt > latest.updatedAt) {
      latest = { updatedAt: query.state.dataUpdatedAt, total: data.pages[0]?.total };
    }
  }
  return latest?.total;
}
