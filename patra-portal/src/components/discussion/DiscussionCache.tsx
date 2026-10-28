"use client";

import { type DehydratedState, hydrate, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";

/// 把服务端取到的讨论区首屏放进客户端缓存，**只作初值**：缓存里还没有数据的查询才放，已经有的一律不动。
/// 不用 `HydrationBoundary`：它对已有的查询会在「服务端那份时间戳更新」时覆盖，而一次在发表之前就开始的服务端
/// 渲染，晚到时时间戳更新、内容却是发表之前的，会把刚发表的评论冲掉（2026-10-28 排查「刚发表的根评论从列表
/// 消失」）。只在挂载时放一次：身份变化清掉讨论缓存之后，也不会把上一位访客的那份首屏再放回去，由查询自己带
/// 新会话重取。
export function DiscussionCache({
  state,
  children,
}: {
  state: DehydratedState;
  children: ReactNode;
}) {
  const client = useQueryClient();
  useState(() => {
    const cache = client.getQueryCache();
    const fresh = state.queries.filter(
      (query) => cache.get(query.queryHash)?.state.data === undefined,
    );
    if (fresh.length > 0) {
      hydrate(client, { queries: fresh });
    }
    return null;
  });
  return children;
}
