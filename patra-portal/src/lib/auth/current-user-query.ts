import { useQuery } from "@tanstack/react-query";
import type { CurrentUser, CurrentUserPayload } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";
import { useHydrated } from "@/lib/use-hydrated";

/// 导航栏看到的登录状态。
export type CurrentUserState =
  | { status: "pending" }
  | { status: "anonymous" }
  | { status: "authenticated"; user: CurrentUser };

/// 请求门户自己的 `/api/auth/me`：2xx 时取 `user`，非 2xx 时抛错。
export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  const res = await fetch("/api/auth/me");
  if (!res.ok) {
    throw new Error(`当前用户加载失败：${res.status}`);
  }
  return ((await res.json()) as CurrentUserPayload).user;
}

/// 当前用户（浏览器端，经 `/api/auth/me` 中转），沿用全局的缓存和重试设置。
/// 还没有数据时（包括失败后重试期间）是 `pending`；最终出错时按未登录处理，这一次显示未登录态。
/// 服务端渲染与水合那一次一律是 `pending`：页面里好几处都读当前用户（导航栏、收藏按钮），先水合的那处
/// 发出的请求可能在后水合的 Suspense 边界水合之前就回来，不压住就和服务端输出的占位对不上
export function useCurrentUser(): CurrentUserState {
  const hydrated = useHydrated();
  const { data, isError } = useQuery({
    queryKey: queryKeys.currentUser,
    queryFn: fetchCurrentUser,
  });
  if (!hydrated) {
    return { status: "pending" };
  }
  if (isError) {
    return { status: "anonymous" };
  }
  if (data === undefined) {
    return { status: "pending" };
  }
  return data === null ? { status: "anonymous" } : { status: "authenticated", user: data };
}
