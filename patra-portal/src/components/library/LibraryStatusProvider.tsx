"use client";

import { type UseQueryResult, useQuery } from "@tanstack/react-query";
import { createContext, type ReactNode, useContext, useMemo } from "react";
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
}

const ScopeContext = createContext<Scope | null>(null);

/// 一屏的「我的状态」：包住列表（或详情页页头），传本屏全部文献 ID，一屏只发一次批量请求（决策 H）。
/// 只在已登录时请求；失败不重试，按钮直接显示「收藏暂不可用」。
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
  });
  return <ScopeContext.Provider value={{ user, query }}>{children}</ScopeContext.Provider>;
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
