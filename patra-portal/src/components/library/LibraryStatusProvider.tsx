"use client";

import { type UseQueryResult, useQuery } from "@tanstack/react-query";
import { createContext, type ReactNode, useContext, useMemo, useState } from "react";
import { type CurrentUserState, useCurrentUser } from "@/lib/auth/current-user-query";
import type { ItemStatus } from "@/lib/interaction/schema";
import { fetchLibraryStatus } from "@/lib/library/queries";
import { queryKeys } from "@/lib/query-keys";

/// 收藏按钮看到的状态（简报 §3.1）：
/// - `pending`：登录状态或「我的状态」还没回来（按钮不响应点击、不画骨架）
/// - `anon`：未登录（点击去登录）
/// - `unavailable`：「我的状态」超时或失败（收藏暂不可用）
/// - `ready`：这篇的状态
export type SaveStatus =
  | { kind: "pending" }
  | { kind: "anon" }
  | { kind: "unavailable" }
  | { kind: "ready"; status: ItemStatus };

interface Scope {
  user: CurrentUserState;
  query: UseQueryResult<ItemStatus[]>;
  /// 每篇第一次拿到的「我收藏了没有」（服务端值，本人在这一屏操作之前），收藏人数 ±1 的基线
  baseline: ReadonlyMap<string, boolean>;
}

const ScopeContext = createContext<Scope | null>(null);

/// 一屏的「我的状态」：包住列表（或详情页页头），传本屏全部文献 ID，一屏只发一次批量请求（决策 H）。
/// 只在已登录时请求；失败不重试，按钮直接显示「收藏暂不可用」，回到页面时再试一次。`keys` 由服务端组件给出（换一份
/// 数组表示来了新的服务端渲染结果，收藏人数的基线随之重置），客户端组件不要每次渲染新建数组传进来。
export function LibraryStatusProvider({
  keys,
  children,
}: {
  keys: readonly string[];
  children: ReactNode;
}) {
  const user = useCurrentUser();
  const unique = useMemo(() => [...new Set(keys)], [keys]);
  const query = useQuery({
    queryKey: queryKeys.libraryStatus(unique),
    queryFn: () => fetchLibraryStatus(unique),
    enabled: user.status === "authenticated" && unique.length > 0,
    retry: false,
    // 出错后回到页面或网络恢复时重取一次：interaction 恢复后不用换页，按钮自己回来（D11）；正常时不多发请求
    refetchOnWindowFocus: (query) => query.state.status === "error",
    refetchOnReconnect: (query) => query.state.status === "error",
  });
  const [baseline, setBaseline] = useState<ReadonlyMap<string, boolean>>(() => new Map());
  // `keys` 只在新的服务端渲染结果到达时换成新数组（换排序、切 tab、router.refresh）：那份结果里的计数已经含本人
  // 之前的收藏 / 取消，基线清空，从此刻的状态重新记，增量回到 0
  const [renderedKeys, setRenderedKeys] = useState(keys);
  if (renderedKeys !== keys) {
    setRenderedKeys(keys);
    setBaseline(new Map());
  }
  // 渲染期间补登：状态第一次到达（或换了一批 key）时记下基线；之后的乐观改写、回滚、重取都不再改它
  const fresh =
    renderedKeys === keys ? (query.data?.filter((status) => !baseline.has(status.key)) ?? []) : [];
  if (fresh.length > 0) {
    setBaseline(
      new Map([...baseline, ...fresh.map((status) => [status.key, status.collected] as const)]),
    );
  }
  return (
    <ScopeContext.Provider value={{ user, query, baseline }}>{children}</ScopeContext.Provider>
  );
}

/// 一篇文献的收藏状态（在 `LibraryStatusProvider` 里用）
export function useSaveStatus(key: string): SaveStatus {
  const scope = useContext(ScopeContext);
  if (scope === null || scope.user.status === "pending") {
    return { kind: "pending" };
  }
  if (scope.user.status === "anonymous") {
    return { kind: "anon" };
  }
  if (scope.query.isError) {
    return { kind: "unavailable" };
  }
  if (scope.query.data === undefined) {
    return { kind: "pending" };
  }
  const status = scope.query.data.find((item) => item.key === key);
  return status ? { kind: "ready", status } : { kind: "unavailable" };
}

/// 本人在这一屏收藏 / 取消收藏带来的收藏人数增量（简报 §3.7、§3.12）：从「我的状态」的翻转推出，乐观收藏
/// 立刻 +1，失败回滚自动回 0。未登录、状态没到或取不到、不在 `LibraryStatusProvider` 里都是 0
export function useCollectDelta(key: string): -1 | 0 | 1 {
  const scope = useContext(ScopeContext);
  const before = scope?.baseline.get(key);
  const now = scope?.query.data?.find((status) => status.key === key)?.collected;
  if (before === undefined || now === undefined || before === now) {
    return 0;
  }
  return now ? 1 : -1;
}
